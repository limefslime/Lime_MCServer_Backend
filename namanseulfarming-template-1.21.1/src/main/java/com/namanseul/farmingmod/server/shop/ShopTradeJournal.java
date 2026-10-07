package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import com.namanseul.farmingmod.network.payload.UiRequestPayload;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import com.namanseul.farmingmod.server.player.PlayerActivityTracker;
import com.namanseul.farmingmod.server.mail.BackendMailBridge;
import com.namanseul.farmingmod.server.mail.MailClaimProtocol;
import com.namanseul.farmingmod.server.mail.MailUiService;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** All player mutations run on the server thread. HTTP workers only carry immutable requests. */
public final class ShopTradeJournal {
    private static final String KEY = "nfsShopTrades";
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Map<UUID, CompoundTag> DEFERRED_REPLY = new java.util.HashMap<>();
    private static final ExecutorService HTTP = Executors.newFixedThreadPool(2, work -> {
        Thread thread = new Thread(work, "nfs-shop-trade");
        thread.setDaemon(true);
        return thread;
    });
    private static int ticks;
    private static long lastWarning;
    private ShopTradeJournal() {}

    public static void requireSettled(ServerPlayer player) {
        if (DIRTY.contains(player.getUUID()) || root(player).contains("pending")) {
            throw new IllegalArgumentException("A trade is still pending. Wait for recovery before changing listings.");
        }
    }

    public static void submit(ServerPlayer player, UiRequestPayload payload, String itemId, int quantity) {
        String clientId = UUID.fromString(payload.requestId()).toString();
        if (!clientId.equals(payload.requestId())) throw new IllegalArgumentException("invalid trade request UUID");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        String type = payload.action() == UiAction.SHOP_BUY ? "buy" : "sell";
        if (DIRTY.contains(player.getUUID())) persist(player);
        notifyDeferred(player);
        CompoundTag journal = root(player).copy();
        CompoundTag completed = journal.getCompound("completed");
        if (completed.contains(clientId)) {
            CompoundTag record = completed.getCompound(clientId);
            match(record, type, itemId, quantity);
            reply(player, clientId, type, record.getBoolean("accepted"), record.getString("result"));
            return;
        }
        if (journal.contains("pending")) {
            CompoundTag pending = journal.getCompound("pending");
            if (!clientId.equals(pending.getString("clientId"))) {
                throw new IllegalArgumentException("An earlier trade is being recovered. Do not submit a new trade.");
            }
            match(pending, type, itemId, quantity);
        } else {
            Item item = resolve(itemId);
            ListTag held = new ListTag();
            List<ItemStack> planned = new ArrayList<>();
            for (ItemStack stack : player.getInventory().items) planned.add(stack.copy());
            if (type.equals("sell")) {
                if (quantity > 1000) throw new IllegalArgumentException("sell quantity must be 1000 or less");
                int remaining = quantity;
                for (ItemStack stack : planned) {
                    if (!stack.isEmpty() && stack.getItem() == item && remaining > 0) {
                        int count = Math.min(remaining, stack.getCount());
                        held.add(stack.copyWithCount(count).save(player.registryAccess()));
                        stack.shrink(count);
                        remaining -= count;
                    }
                }
                if (remaining != 0) throw new IllegalArgumentException("not enough items in main inventory");
            } else if (TradeInventoryPlan.insert(planned, List.of(new ItemStack(item, quantity))) == null) {
                throw new IllegalArgumentException("inventory full; free space before buying");
            }
            CompoundTag pending = new CompoundTag();
            pending.putString("clientId", clientId);
            // Backend IDs are server-generated and separate from client-controlled UI IDs.
            pending.putString("requestId", UUID.randomUUID().toString());
            pending.putString("transactionType", type);
            pending.putString("itemId", itemId);
            pending.putInt("quantity", quantity);
            pending.putString("phase", "queued");
            pending.put("held", held);
            journal.put("pending", pending);
            applyInventory(player, planned);
            player.getPersistentData().put(KEY, journal);
            persist(player); // No HTTP is sent until the matching player save is verified and fsynced.
        }
        JsonObject waiting = new JsonObject();
        waiting.addProperty("pending", true);
        sendSuccess(player, clientId, type, waiting.toString());
        recover(player);
    }

    public static void submitMail(ServerPlayer player, UiRequestPayload payload, String mailId) {
        String clientId = UUID.fromString(payload.requestId()).toString();
        if (!clientId.equals(payload.requestId())) throw new IllegalArgumentException("invalid mail request UUID");
        mailId = UUID.fromString(mailId).toString();
        if (DIRTY.contains(player.getUUID())) persist(player);
        notifyDeferred(player);
        CompoundTag journal = root(player).copy();
        CompoundTag completed = journal.getCompound("completed");
        if (completed.contains(clientId)) {
            CompoundTag record = completed.getCompound(clientId);
            match(record, "mail", mailId, 0);
            reply(player, clientId, "mail", record.getBoolean("accepted"), record.getString("result"));
            return;
        }
        if (journal.contains("pending")) {
            CompoundTag pending = journal.getCompound("pending");
            if (!clientId.equals(pending.getString("clientId"))) {
                throw new IllegalArgumentException("An earlier trade or mail claim is still being recovered.");
            }
            match(pending, "mail", mailId, 0);
        } else {
            CompoundTag pending = new CompoundTag();
            pending.putString("clientId", clientId);
            pending.putString("requestId", UUID.randomUUID().toString());
            pending.putString("transactionType", "mail");
            pending.putString("itemId", mailId);
            pending.putInt("quantity", 0);
            pending.putString("phase", "queued");
            journal.put("pending", pending);
            player.getPersistentData().put(KEY, journal);
            persist(player);
        }
        JsonObject waiting = new JsonObject();
        waiting.addProperty("pending", true);
        sendSuccess(player, clientId, "mail", waiting.toString());
        recover(player);
    }

    public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // NeoForge replaces the player on death; custom escrow must survive that replacement.
        CompoundTag original = event.getOriginal().getPersistentData();
        for (String key : new String[] {KEY, "nfsShopListings"}) {
            if (original.contains(key)) player.getPersistentData().put(key, original.getCompound(key).copy());
        }
    }

    public static void stopped(ServerStoppedEvent event) {
        BUSY.clear();
        DIRTY.clear();
        DEFERRED_REPLY.clear();
        ticks = 0;
    }

    public static void tick(ServerTickEvent.Post event) {
        if (++ticks < 100) return;
        ticks = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            try {
                if (DIRTY.contains(player.getUUID())) persist(player);
                notifyDeferred(player);
                recover(player);
            } catch (Exception ex) { warn(); }
        }
    }

    private static void recover(ServerPlayer player) {
        CompoundTag pending = root(player).getCompound("pending");
        if (pending.isEmpty()) return;
        if (!pending.getString("phase").equals("queued")) {
            finalizeTrade(player);
            return;
        }
        String requestId = pending.getString("requestId");
        if (!BUSY.add(requestId)) return;
        try { persist(player); }
        catch (Exception ex) { BUSY.remove(requestId); throw ex; }
        ShopTradeProtocol.Request request = new ShopTradeProtocol.Request(player.getUUID().toString(), requestId,
                pending.getString("transactionType"), pending.getString("itemId"), pending.getInt("quantity"));
        var server = player.getServer();
        HTTP.execute(() -> {
            ShopTradeProtocol.Outcome outcome;
            try {
                if (request.transactionType().equals("mail")) {
                    var mail = BackendMailBridge.claimReceipt(request.playerId(), request.itemId(), request.requestId());
                    outcome = new ShopTradeProtocol.Outcome(mail.accepted(), mail.result());
                } else outcome = BackendShopBridge.trade(request);
            }
            catch (Exception ex) { BUSY.remove(requestId); return; }
            server.execute(() -> {
                try {
                    // A disconnected/replaced player is recovered from their saved queue on login.
                    if (server.getPlayerList().getPlayer(player.getUUID()) != player) return;
                    CompoundTag journal = root(player).copy();
                    CompoundTag current = journal.getCompound("pending");
                    if (!requestId.equals(current.getString("requestId"))) return;
                    current.putString("phase", outcome.accepted() ? "accepted" : "rejected");
                    current.putString("result", outcome.result().toString());
                    journal.put("pending", current);
                    player.getPersistentData().put(KEY, journal);
                    persist(player);
                    finalizeTrade(player);
                } catch (Exception ex) { warn(); }
                finally { BUSY.remove(requestId); }
            });
        });
    }

    private static void finalizeTrade(ServerPlayer player) {
        CompoundTag journal = root(player).copy();
        CompoundTag pending = journal.getCompound("pending");
        String phase = pending.getString("phase");
        if (!phase.equals("accepted") && !phase.equals("rejected")) {
            throw new IllegalStateException("invalid saved trade phase; retained for recovery");
        }
        boolean accepted = phase.equals("accepted");
        String type = pending.getString("transactionType");
        // Re-validate persisted responses before trusting them after a restart.
        JsonObject result = JsonParser.parseString(pending.getString("result")).getAsJsonObject();
        ShopTradeProtocol.Request request = new ShopTradeProtocol.Request(player.getUUID().toString(),
                pending.getString("requestId"), type, pending.getString("itemId"), pending.getInt("quantity"));
        int status = accepted ? 200 : rejectionStatus(result.get("code").getAsString());
        boolean verified = type.equals("mail")
                ? MailClaimProtocol.parse(request.playerId(), request.requestId(), request.itemId(), status, result.toString()).accepted()
                : ShopTradeProtocol.parse(request, status, result.toString()).accepted();
        if (verified != accepted) {
            throw new IllegalStateException("invalid saved trade outcome");
        }
        List<ItemStack> returning = new ArrayList<>();
        if (accepted && type.equals("mail")) {
            var item = result.getAsJsonObject("rewardInfo").get("itemReward");
            if (!item.isJsonNull()) {
                JsonObject reward = item.getAsJsonObject();
                returning.add(new ItemStack(resolve(reward.get("itemId").getAsString()), reward.get("quantity").getAsInt()));
            }
        } else if (accepted && type.equals("buy")) {
            returning.add(new ItemStack(resolve(pending.getString("itemId")), pending.getInt("quantity")));
        } else if (!accepted && type.equals("sell")) {
            ListTag held = pending.getList("held", 10);
            for (int i = 0; i < held.size(); i++) {
                ItemStack stack = ItemStack.parse(player.registryAccess(), held.getCompound(i))
                        .orElseThrow(() -> new IllegalStateException("unreadable sale escrow; retained for recovery"));
                returning.add(stack);
            }
            int count = returning.stream().mapToInt(ItemStack::getCount).sum();
            if (count != pending.getInt("quantity")) throw new IllegalStateException("escrow quantity mismatch");
        }
        List<ItemStack> planned = TradeInventoryPlan.insert(player.getInventory().items, returning);
        if (planned == null) return; // Whole delivery/refund waits for space; never drop or partially insert.
        CompoundTag completed = journal.getCompound("completed").copy();
        CompoundTag record = pending.copy();
        record.remove("held");
        record.putBoolean("accepted", accepted);
        String clientId = pending.getString("clientId");
        completed.put(clientId, record);
        journal.put("completed", completed);
        journal.remove("pending");
        applyInventory(player, planned);
        player.getPersistentData().put(KEY, journal);
        DEFERRED_REPLY.put(player.getUUID(), record);
        // Never roll back an uncertain save: the disk may already contain this completed inventory.
        persist(player);
        ShopUiService.invalidateReadCaches();
        if (accepted) {
            try {
                if (type.equals("mail")) PlayerActivityTracker.recordMailClaim(player.getUUID(), result);
                else PlayerActivityTracker.recordShopTrade(player.getUUID(), action(type), result);
            }
            catch (Exception ignored) { /* ancillary tracking does not undo delivery */ }
        }
        notifyDeferred(player);
    }

    private static void notifyDeferred(ServerPlayer player) {
        CompoundTag record = DEFERRED_REPLY.remove(player.getUUID());
        if (record == null) return;
        String clientId = record.getString("clientId");
        // Reconnected players may have loaded an earlier atomic save and still be pending.
        if (!root(player).getCompound("completed").contains(clientId)) return;
        ShopUiService.invalidateReadCaches();
        MailUiService.invalidate(player.getUUID());
        reply(player, clientId, record.getString("transactionType"), record.getBoolean("accepted"), record.getString("result"));
    }

    private static int rejectionStatus(String code) {
        return switch (code) {
            case "INVALID_INPUT", "ITEM_PRICE_NOT_TRADABLE", "SELL_QUANTITY_TOO_LARGE" -> 400;
            case "ITEM_NOT_FOUND", "MAIL_NOT_FOUND" -> 404;
            case "TRADE_COOLDOWN_ACTIVE" -> 429;
            default -> 409;
        };
    }
    private static void match(CompoundTag record, String type, String itemId, int quantity) {
        if (!type.equals(record.getString("transactionType")) || !itemId.equals(record.getString("itemId"))
                || quantity != record.getInt("quantity")) throw new IllegalArgumentException("UI request ID reused for another trade");
    }
    private static CompoundTag root(ServerPlayer player) { return player.getPersistentData().getCompound(KEY); }
    private static Item resolve(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException("unknown shop item");
        return item;
    }
    private static void applyInventory(ServerPlayer player, List<ItemStack> planned) {
        for (int i = 0; i < planned.size(); i++) player.getInventory().items.set(i, planned.get(i));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
    private static void persist(ServerPlayer player) {
        DIRTY.add(player.getUUID());
        try {
            CompoundTag expected = player.saveWithoutId(new CompoundTag());
            player.getServer().getPlayerList().save(player);
            Path file = player.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(player.getUUID() + ".dat");
            CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (!expected.getList("Inventory", 10).equals(saved.getList("Inventory", 10))
                    || !expected.getCompound("NeoForgeData").getCompound(KEY)
                    .equals(saved.getCompound("NeoForgeData").getCompound(KEY))) {
                throw new IllegalStateException("player save does not contain trade inventory and journal");
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
            try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) { directory.force(true); }
            DIRTY.remove(player.getUUID());
        } catch (Exception ex) {
            throw new IllegalStateException("Trade retained locally; player save could not be verified. Check server storage.", ex);
        }
    }
    private static UiAction action(String type) {
        return type.equals("mail") ? UiAction.MAIL_CLAIM : type.equals("buy") ? UiAction.SHOP_BUY : UiAction.SHOP_SELL;
    }
    private static UiScreenType screen(String type) { return type.equals("mail") ? UiScreenType.MAIL : UiScreenType.SHOP; }
    private static void sendSuccess(ServerPlayer player, String clientId, String type, String json) {
        PacketDistributor.sendToPlayer(player, UiResponsePayload.successJson(clientId, screen(type), action(type), json));
    }
    private static void reply(ServerPlayer player, String clientId, String type, boolean accepted, String json) {
        if (accepted) sendSuccess(player, clientId, type, json);
        else PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(clientId, screen(type), action(type),
                JsonParser.parseString(json).getAsJsonObject().get("message").getAsString()));
    }
    private static void warn() {
        if (System.currentTimeMillis() - lastWarning > 60000) {
            NamanseulFarming.LOGGER.warn("[Shop] trade recovery pending; check player storage and inventory space");
            lastWarning = System.currentTimeMillis();
        }
    }
}
