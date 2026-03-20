package com.namanseul.farmingmod.network;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import com.namanseul.farmingmod.network.payload.UiRequestPayload;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import com.namanseul.farmingmod.server.mail.BackendMailBridge;
import com.namanseul.farmingmod.server.mail.MailUiService;
import com.namanseul.farmingmod.server.invest.BackendInvestBridge;
import com.namanseul.farmingmod.server.invest.InvestUiService;
import com.namanseul.farmingmod.server.player.BackendPlayerBridge;
import com.namanseul.farmingmod.server.player.PlayerActivityTracker;
import com.namanseul.farmingmod.server.player.PlayerOverviewUiService;
import com.namanseul.farmingmod.server.status.BackendStatusBridge;
import com.namanseul.farmingmod.server.status.StatusUiService;
import com.namanseul.farmingmod.server.shop.BackendShopBridge;
import com.namanseul.farmingmod.server.shop.PlayerShopListingService;
import com.namanseul.farmingmod.server.shop.ShopUiService;
import com.namanseul.farmingmod.server.summary.HubSummaryService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

public final class UiServerPayloadHandlers {
    private static final Gson GSON = new Gson();

    private UiServerPayloadHandlers() {}

    public static void handleRequest(UiRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] C->S requestId={} screen={} action={} player={}",
                    payload.requestId(),
                    payload.screenType().serialized(),
                    payload.action().serialized(),
                    player.getGameProfile().getName()
            );
        }

        try {
            switch (payload.screenType()) {
                case HUB -> handleHubRequest(payload, player);
                case SHOP -> handleShopRequest(payload, player);
                case MAIL -> handleMailRequest(payload, player);
                case INVEST -> handleInvestRequest(payload, player);
                case STATUS -> handleStatusRequest(payload, player);
                case PLAYER -> handlePlayerRequest(payload, player);
            }
        } catch (IllegalArgumentException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Request validation failed id={} screen={} action={} error={}",
                    payload.requestId(),
                    payload.screenType().serialized(),
                    payload.action().serialized(),
                    ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (BackendShopBridge.ShopBridgeException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Shop bridge failed id={} action={} error={}",
                    payload.requestId(), payload.action().serialized(), ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (BackendMailBridge.MailBridgeException | MailUiService.MailUiException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Mail bridge failed id={} action={} error={}",
                    payload.requestId(), payload.action().serialized(), ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (BackendInvestBridge.InvestBridgeException | InvestUiService.InvestUiException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Invest bridge failed id={} action={} error={}",
                    payload.requestId(), payload.action().serialized(), ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (BackendStatusBridge.StatusBridgeException | StatusUiService.StatusUiException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Status bridge failed id={} action={} error={}",
                    payload.requestId(), payload.action().serialized(), ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (BackendPlayerBridge.PlayerBridgeException | PlayerOverviewUiService.PlayerOverviewUiException ex) {
            NamanseulFarming.LOGGER.warn("[UI] Player bridge failed id={} action={} error={}",
                    payload.requestId(), payload.action().serialized(), ex.getMessage());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    ex.getMessage()
            ));
        } catch (Exception ex) {
            NamanseulFarming.LOGGER.warn("[UI] Request failed id={} screen={} action={} error={}",
                    payload.requestId(),
                    payload.screenType().serialized(),
                    payload.action().serialized(),
                    ex.toString());
            PacketDistributor.sendToPlayer(player, UiResponsePayload.failed(
                    payload.requestId(),
                    payload.screenType(),
                    payload.action(),
                    "ui request failed"
            ));
        }
    }

    private static void handleHubRequest(UiRequestPayload payload, ServerPlayer player) {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openHub(payload.requestId()));
            return;
        }

        if (payload.action() != UiAction.INIT
                && payload.action() != UiAction.SUMMARY
                && payload.action() != UiAction.REFRESH) {
            throw new IllegalArgumentException("unsupported hub action");
        }

        boolean forceRefresh = payload.action() == UiAction.REFRESH;
        HubSummaryData summary = HubSummaryService.getSummary(player, forceRefresh);
        UiResponsePayload response = UiResponsePayload.successSummary(payload.requestId(), payload.action(), summary);
        PacketDistributor.sendToPlayer(player, response);

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=hub action={} partial={}",
                    payload.requestId(),
                    payload.action().serialized(),
                    summary.partial()
            );
        }
    }

    private static void handleShopRequest(UiRequestPayload payload, ServerPlayer player)
            throws BackendShopBridge.ShopBridgeException, BackendMailBridge.MailBridgeException {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openShop(payload.requestId()));
            return;
        }

        JsonObject requestPayload = parsePayloadObject(payload.payloadJson());
        JsonElement result;
        switch (payload.action()) {
            case SHOP_LIST -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                JsonElement backend = ShopUiService.listShopItems(forceRefresh);
                result = PlayerShopListingService.mergeShopList(player.getUUID(), backend);
            }
            case SHOP_DETAIL -> {
                String itemId = readItemId(requestPayload);
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                JsonObject listing = PlayerShopListingService.getListing(player.getUUID(), itemId);
                if (listing != null) {
                    result = listing;
                } else {
                    JsonElement backend = ShopUiService.getShopItem(itemId, forceRefresh);
                    result = PlayerShopListingService.resolveShopDetail(player.getUUID(), itemId, backend);
                }
            }
            case SHOP_PREVIEW_BUY -> {
                String itemId = readItemId(requestPayload);
                int quantity = readQuantity(requestPayload);
                result = ShopUiService.previewBuy(player.getUUID(), itemId, quantity);
            }
            case SHOP_PREVIEW_SELL -> {
                String itemId = readItemId(requestPayload);
                int quantity = readQuantity(requestPayload);
                result = ShopUiService.previewSell(player.getUUID(), itemId, quantity);
            }
            case SHOP_BUY -> {
                String itemId = readItemId(requestPayload);
                int quantity = readQuantity(requestPayload);
                resolveItem(itemId);
                result = ShopUiService.buy(player.getUUID(), itemId, quantity);
                grantPlayerInventoryItem(player, itemId, quantity);
                PlayerShopListingService.adjustListingQuantity(player.getUUID(), itemId, -quantity);
            }
            case SHOP_SELL -> {
                String itemId = readItemId(requestPayload);
                int quantity = readQuantity(requestPayload);
                ensurePlayerHasInventoryItem(player, itemId, quantity);
                result = ShopUiService.sell(player.getUUID(), itemId, quantity);
                consumePlayerInventoryItem(player, itemId, quantity, -1);
                PlayerShopListingService.adjustListingQuantity(player.getUUID(), itemId, quantity);
            }
            case SHOP_REGISTER -> {
                String itemId = readItemId(requestPayload);
                int quantity = readQuantity(requestPayload);
                String itemName = readOptionalString(requestPayload, "itemName", itemId);
                int slot = readInt(requestPayload, "slot", -1);
                consumePlayerInventoryItem(player, itemId, quantity, slot);
                JsonObject listing = PlayerShopListingService.registerListing(player.getUUID(), itemId, itemName, quantity);
                JsonObject response = new JsonObject();
                response.addProperty("registered", true);
                response.add("listing", listing);
                result = response;
            }
            case SHOP_CANCEL_SELL -> {
                String itemId = readItemId(requestPayload);
                JsonObject listing = PlayerShopListingService.getListing(player.getUUID(), itemId);
                if (listing == null) {
                    throw new IllegalArgumentException("listing not found");
                }
                int quantity = Math.max(1, readInt(listing, "listingQuantity", 1));
                String itemName = readOptionalString(listing, "itemName", itemId);
                JsonElement mailResult = BackendMailBridge.sendItemRewardMail(
                        player.getUUID().toString(),
                        "Shop Sell Canceled",
                        "Canceled sell listing has been returned by mail.",
                        itemId,
                        quantity
                );
                JsonObject removed = PlayerShopListingService.removeListing(player.getUUID(), itemId);
                JsonObject response = new JsonObject();
                response.addProperty("canceled", removed != null);
                if (removed != null) {
                    response.add("listing", removed);
                }
                response.add("mail", mailResult);
                response.addProperty("itemName", itemName);
                result = response;
            }
            default -> throw new IllegalArgumentException("unsupported shop action");
        }

        if (payload.action() == UiAction.SHOP_BUY || payload.action() == UiAction.SHOP_SELL) {
            try {
                PlayerActivityTracker.recordShopTrade(player.getUUID(), payload.action(), result);
            } catch (Exception ignored) {
                // activity tracking must never break core flow
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                UiResponsePayload.successJson(
                        payload.requestId(),
                        UiScreenType.SHOP,
                        payload.action(),
                        GSON.toJson(result)
                )
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=shop action={}",
                    payload.requestId(),
                    payload.action().serialized()
            );
        }
    }

    private static void handleMailRequest(UiRequestPayload payload, ServerPlayer player)
            throws BackendMailBridge.MailBridgeException, MailUiService.MailUiException {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openMail(payload.requestId()));
            return;
        }

        JsonObject requestPayload = parsePayloadObject(payload.payloadJson());
        JsonElement result;
        switch (payload.action()) {
            case MAIL_LIST -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = MailUiService.listMailbox(player.getUUID(), forceRefresh);
            }
            case MAIL_REFRESH -> {
                result = MailUiService.listMailbox(player.getUUID(), true);
            }
            case MAIL_DETAIL -> {
                String mailId = readMailId(requestPayload);
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = MailUiService.getMailDetail(player.getUUID(), mailId, forceRefresh);
            }
            case MAIL_CLAIM -> {
                String mailId = readMailId(requestPayload);
                result = MailUiService.claim(player.getUUID(), mailId);
                applyMailClaimItemReward(player, result);
            }
            default -> throw new IllegalArgumentException("unsupported mail action");
        }

        if (payload.action() == UiAction.MAIL_CLAIM) {
            try {
                PlayerActivityTracker.recordMailClaim(player.getUUID(), result);
            } catch (Exception ignored) {
                // activity tracking must never break core flow
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                UiResponsePayload.successJson(
                        payload.requestId(),
                        UiScreenType.MAIL,
                        payload.action(),
                        GSON.toJson(result)
                )
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=mail action={}",
                    payload.requestId(),
                    payload.action().serialized()
            );
        }
    }

    private static void handleInvestRequest(UiRequestPayload payload, ServerPlayer player)
            throws BackendInvestBridge.InvestBridgeException, InvestUiService.InvestUiException {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openInvest(payload.requestId()));
            return;
        }

        JsonObject requestPayload = parsePayloadObject(payload.payloadJson());
        JsonElement result;
        switch (payload.action()) {
            case INVEST_LIST -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = InvestUiService.listProjects(player.getUUID(), forceRefresh);
            }
            case INVEST_REFRESH -> {
                result = InvestUiService.listProjects(player.getUUID(), true);
            }
            case INVEST_DETAIL -> {
                String projectId = readProjectId(requestPayload);
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = InvestUiService.getProjectDetail(player.getUUID(), projectId, forceRefresh);
            }
            case INVEST_PROGRESS -> {
                String projectId = readProjectId(requestPayload);
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = InvestUiService.getProjectProgress(player.getUUID(), projectId, forceRefresh);
            }
            case INVEST_CONTRIBUTE -> {
                String projectId = readProjectId(requestPayload);
                int amount = readAmount(requestPayload);
                result = InvestUiService.invest(player.getUUID(), projectId, amount);
            }
            default -> throw new IllegalArgumentException("unsupported invest action");
        }

        if (payload.action() == UiAction.INVEST_CONTRIBUTE) {
            try {
                PlayerActivityTracker.recordInvest(player.getUUID(), result);
            } catch (Exception ignored) {
                // activity tracking must never break core flow
            }
        }

        PacketDistributor.sendToPlayer(
                player,
                UiResponsePayload.successJson(
                        payload.requestId(),
                        UiScreenType.INVEST,
                        payload.action(),
                        GSON.toJson(result)
                )
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=invest action={}",
                    payload.requestId(),
                    payload.action().serialized()
            );
        }
    }

    private static void handleStatusRequest(UiRequestPayload payload, ServerPlayer player)
            throws BackendStatusBridge.StatusBridgeException, StatusUiService.StatusUiException {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openStatus(payload.requestId()));
            return;
        }

        JsonObject requestPayload = parsePayloadObject(payload.payloadJson());
        JsonElement result;
        switch (payload.action()) {
            case STATUS_OVERVIEW -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = StatusUiService.getOverview(forceRefresh);
            }
            case STATUS_REFRESH -> result = StatusUiService.getOverview(true);
            default -> throw new IllegalArgumentException("unsupported status action");
        }

        PacketDistributor.sendToPlayer(
                player,
                UiResponsePayload.successJson(
                        payload.requestId(),
                        UiScreenType.STATUS,
                        payload.action(),
                        GSON.toJson(result)
                )
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=status action={}",
                    payload.requestId(),
                    payload.action().serialized()
            );
        }
    }

    private static void handlePlayerRequest(UiRequestPayload payload, ServerPlayer player)
            throws BackendPlayerBridge.PlayerBridgeException, PlayerOverviewUiService.PlayerOverviewUiException {
        if (payload.action() == UiAction.OPEN) {
            PacketDistributor.sendToPlayer(player, UiResponsePayload.openPlayer(payload.requestId()));
            return;
        }

        JsonObject requestPayload = parsePayloadObject(payload.payloadJson());
        JsonElement result;
        switch (payload.action()) {
            case PLAYER_OVERVIEW -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = PlayerOverviewUiService.getOverview(player.getUUID(), forceRefresh);
            }
            case PLAYER_WALLET -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = PlayerOverviewUiService.getWallet(player.getUUID(), forceRefresh);
            }
            case PLAYER_ACTIVITY -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = PlayerOverviewUiService.getActivity(player.getUUID(), forceRefresh);
            }
            case PLAYER_SUMMARY -> {
                boolean forceRefresh = readBoolean(requestPayload, "forceRefresh");
                result = PlayerOverviewUiService.getSummary(player.getUUID(), forceRefresh);
            }
            case PLAYER_REFRESH -> result = PlayerOverviewUiService.getOverview(player.getUUID(), true);
            default -> throw new IllegalArgumentException("unsupported player action");
        }

        PacketDistributor.sendToPlayer(
                player,
                UiResponsePayload.successJson(
                        payload.requestId(),
                        UiScreenType.PLAYER,
                        payload.action(),
                        GSON.toJson(result)
                )
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] S->C responseId={} success=true screen=player action={}",
                    payload.requestId(),
                    payload.action().serialized()
            );
        }
    }

    private static JsonObject parsePayloadObject(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new JsonObject();
        }

        try {
            JsonElement parsed = JsonParser.parseString(payloadJson);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("payload must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("payload json is invalid");
        }
    }

    private static boolean readBoolean(JsonObject payload, String key) {
        JsonElement value = payload.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int readInt(JsonObject payload, String key, int fallback) {
        JsonElement value = payload.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readOptionalString(JsonObject payload, String key, String fallback) {
        JsonElement value = payload.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            String raw = value.getAsString();
            return raw == null || raw.isBlank() ? fallback : raw.trim();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readItemId(JsonObject payload) {
        JsonElement value = payload.get("itemId");
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("itemId is required");
        }
        String itemId = value.getAsString();
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        return itemId.trim();
    }

    private static int readQuantity(JsonObject payload) {
        JsonElement value = payload.get("quantity");
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("quantity is required");
        }
        int quantity;
        try {
            quantity = value.getAsInt();
        } catch (Exception ex) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be a positive integer");
        }
        return quantity;
    }

    private static String readMailId(JsonObject payload) {
        JsonElement value = payload.get("mailId");
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("mailId is required");
        }
        String mailId = value.getAsString();
        if (mailId == null || mailId.isBlank()) {
            throw new IllegalArgumentException("mailId is required");
        }
        return mailId.trim();
    }

    private static String readProjectId(JsonObject payload) {
        JsonElement value = payload.get("projectId");
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("projectId is required");
        }
        String projectId = value.getAsString();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        return projectId.trim();
    }

    private static int readAmount(JsonObject payload) {
        JsonElement value = payload.get("amount");
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("amount is required");
        }
        int amount;
        try {
            amount = value.getAsInt();
        } catch (Exception ex) {
            throw new IllegalArgumentException("amount must be a positive integer");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be a positive integer");
        }
        return amount;
    }

    private static void consumePlayerInventoryItem(ServerPlayer player, String itemId, int quantity, int preferredSlot) {
        Item targetItem = resolveItem(itemId);
        int available = countMatchingItems(player, targetItem);
        if (available < quantity) {
            throw new IllegalArgumentException("not enough item quantity in inventory");
        }

        int remaining = quantity;
        if (preferredSlot >= 0 && preferredSlot < player.getInventory().items.size()) {
            ItemStack slotStack = player.getInventory().items.get(preferredSlot);
            if (matchesItem(slotStack, targetItem)) {
                int consumed = Math.min(remaining, slotStack.getCount());
                slotStack.shrink(consumed);
                remaining -= consumed;
            }
        }

        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            if (i == preferredSlot) {
                continue;
            }
            ItemStack stack = player.getInventory().items.get(i);
            if (!matchesItem(stack, targetItem)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }

        for (int i = 0; i < player.getInventory().offhand.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (!matchesItem(stack, targetItem)) {
                continue;
            }
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }

        if (remaining > 0) {
            throw new IllegalArgumentException("failed to consume requested item quantity");
        }

        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static void ensurePlayerHasInventoryItem(ServerPlayer player, String itemId, int quantity) {
        Item targetItem = resolveItem(itemId);
        int available = countMatchingItems(player, targetItem);
        if (available < quantity) {
            throw new IllegalArgumentException("not enough item quantity in inventory");
        }
    }

    private static void grantPlayerInventoryItem(ServerPlayer player, String itemId, int quantity) {
        Item item = resolveItem(itemId);
        int remaining = quantity;
        int maxStack = Math.max(1, item.getDefaultMaxStackSize());
        while (remaining > 0) {
            int nextCount = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, nextCount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= nextCount;
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    private static int countMatchingItems(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matchesItem(stack, item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (matchesItem(stack, item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean matchesItem(ItemStack stack, Item item) {
        return stack != null && !stack.isEmpty() && stack.getItem() == item;
    }

    private static Item resolveItem(String itemId) {
        Item item = null;
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location != null) {
            item = BuiltInRegistries.ITEM.get(location);
        }
        if ((item == null || item == Items.AIR) && itemId != null && !itemId.isBlank() && !itemId.contains(":")) {
            ResourceLocation namespaced = ResourceLocation.tryParse("minecraft:" + itemId);
            if (namespaced != null) {
                item = BuiltInRegistries.ITEM.get(namespaced);
            }
        }
        if (item == null || item == Items.AIR) {
            throw new IllegalArgumentException("itemId is unknown");
        }
        return item;
    }

    private static void applyMailClaimItemReward(ServerPlayer player, JsonElement claimPayload) {
        JsonObject itemReward = findItemRewardObject(claimPayload);
        if (itemReward == null) {
            return;
        }

        String itemId = readOptionalString(itemReward, "itemId", "");
        int quantity = readInt(itemReward, "quantity", 0);
        if (itemId.isBlank() || quantity <= 0) {
            return;
        }

        try {
            grantPlayerInventoryItem(player, itemId, quantity);
        } catch (Exception ex) {
            NamanseulFarming.LOGGER.warn("[UI] mail item reward grant skipped player={} itemId={} quantity={} error={}",
                    player.getGameProfile().getName(), itemId, quantity, ex.toString());
        }
    }

    @Nullable
    private static JsonObject findItemRewardObject(JsonElement payload) {
        if (payload == null || !payload.isJsonObject()) {
            return null;
        }

        JsonObject root = payload.getAsJsonObject();
        JsonObject direct = readObject(root, "itemReward");
        if (direct != null) {
            return direct;
        }

        JsonObject rewardInfo = readObject(root, "rewardInfo");
        JsonObject nested = readObject(rewardInfo, "itemReward");
        if (nested != null) {
            return nested;
        }

        JsonObject mail = readObject(root, "mail");
        JsonObject mailRewardInfo = readObject(mail, "rewardInfo");
        JsonObject mailItemReward = readObject(mailRewardInfo, "itemReward");
        if (mailItemReward != null) {
            return mailItemReward;
        }

        return readObject(mail, "itemReward");
    }

    @Nullable
    private static JsonObject readObject(@Nullable JsonObject source, String key) {
        if (source == null) {
            return null;
        }
        JsonElement element = source.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }
}
