package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class PlayerShopListingService {
    private static final String STORAGE_KEY = "nfsShopListings";

    private PlayerShopListingService() {}

    public static JsonElement mergeShopList(UUID playerUuid, JsonElement backendResult) {
        JsonArray merged = extractShopArray(backendResult);
        Map<String, ListingEntry> playerListings = readListings(playerUuid);
        if (playerListings == null || playerListings.isEmpty()) {
            return merged;
        }

        Set<String> applied = new HashSet<>();
        for (JsonElement entry : merged) {
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getAsJsonObject();
            String itemId = readString(item, "itemId");
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            ListingEntry listing = playerListings.get(itemId);
            if (listing == null) {
                continue;
            }

            applyListingMetadata(item, listing);
            applied.add(itemId);
        }

        for (ListingEntry entry : playerListings.values()) {
            if (applied.contains(entry.itemId)) {
                continue;
            }
            merged.add(entry.toShopItemJson());
        }
        return merged;
    }

    public static JsonElement resolveShopDetail(UUID playerUuid, String itemId, JsonElement backendResult) {
        ListingEntry listing = getListingEntry(playerUuid, itemId);
        if (listing == null) {
            return backendResult;
        }

        if (backendResult != null && backendResult.isJsonObject()) {
            JsonObject merged = backendResult.getAsJsonObject().deepCopy();
            applyListingMetadata(merged, listing);
            return merged;
        }

        return listing.toShopItemJson();
    }

    /** Inventory and escrow share the same player save. Call only on the server thread. */
    static JsonObject registerListing(ServerPlayer player, String itemId, int quantity, int preferredSlot) {
        ListingEntry existing = readListings(player.getUUID()).get(itemId);
        if (existing != null) Math.addExact(existing.quantity, quantity);
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        List<ItemStack> inventory = player.getInventory().items;
        List<Integer> slots = new ArrayList<>();
        if (preferredSlot >= 0 && preferredSlot < inventory.size()) slots.add(preferredSlot);
        for (int i = 0; i < inventory.size(); i++) if (i != preferredSlot) slots.add(i);
        List<ItemStack> captured = new ArrayList<>();
        Map<Integer, Integer> taken = new LinkedHashMap<>();
        int remaining = quantity;
        for (int slot : slots) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) continue;
            int count = Math.min(remaining, stack.getCount());
            if (count == 0) break;
            captured.add(stack.copyWithCount(count));
            taken.put(slot, count);
            remaining -= count;
        }
        if (remaining != 0) throw new IllegalArgumentException("not enough items in main inventory");
        CompoundTag listings = player.getPersistentData().getCompound(STORAGE_KEY).copy();
        CompoundTag entry = listings.getCompound(itemId).copy();
        ListTag stacks = entry.getList("stacks", 10).copy();
        // Serialize before mutating inventory: codec errors leave the player's items untouched.
        for (ItemStack stack : captured) stacks.add(stack.save(player.registryAccess()));
        entry.put("stacks", stacks);
        if (!entry.contains("createdAt")) entry.putLong("createdAt", System.currentTimeMillis());
        listings.put(itemId, entry);
        int total = Math.addExact(existing == null ? 0 : existing.quantity, quantity);
        String name = existing == null ? captured.getFirst().getHoverName().getString() : existing.itemName;
        JsonObject result = new ListingEntry(itemId, name, "player_listing", total, entry.getLong("createdAt")).toShopItemJson();
        for (var take : taken.entrySet()) inventory.get(take.getKey()).shrink(take.getValue());
        player.getPersistentData().put(STORAGE_KEY, listings);
        return result;
    }

    public static JsonObject getListing(UUID playerUuid, String itemId) {
        ListingEntry entry = getListingEntry(playerUuid, itemId);
        return entry == null ? null : entry.toShopItemJson();
    }

    static JsonObject cancelListing(ServerPlayer player, String itemId) {
        JsonObject listing = getListing(player.getUUID(), itemId);
        if (listing == null) throw new IllegalArgumentException("listing not found");
        CompoundTag listings = player.getPersistentData().getCompound(STORAGE_KEY).copy();
        ListTag stored = listings.getCompound(itemId).getList("stacks", 10);
        List<ItemStack> planned = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) planned.add(stack.copy());
        for (int i = 0; i < stored.size(); i++) {
            ItemStack returning = ItemStack.parse(player.registryAccess(), stored.getCompound(i))
                    .orElseThrow(() -> new IllegalStateException("listing contains an unreadable item; retained for recovery"));
            if (returning.isEmpty()) throw new IllegalStateException("listing contains an empty item; retained for recovery");
            for (ItemStack target : planned) {
                if (!target.isEmpty() && ItemStack.isSameItemSameComponents(target, returning)) {
                    int count = Math.min(returning.getCount(), Math.max(0, Math.min(64, target.getMaxStackSize()) - target.getCount()));
                    target.grow(count);
                    returning.shrink(count);
                }
            }
            for (int slot = 0; slot < planned.size() && !returning.isEmpty(); slot++) {
                if (planned.get(slot).isEmpty()) {
                    int count = Math.min(returning.getCount(), Math.min(64, returning.getMaxStackSize()));
                    planned.set(slot, returning.copyWithCount(count));
                    returning.shrink(count);
                }
            }
            if (!returning.isEmpty()) throw new IllegalArgumentException("inventory full; listing retained, free space and retry");
        }
        for (int i = 0; i < planned.size(); i++) player.getInventory().items.set(i, planned.get(i));
        listings.remove(itemId);
        player.getPersistentData().put(STORAGE_KEY, listings);
        return listing;
    }

    private static Map<String, ListingEntry> readListings(UUID playerUuid) {
        Map<String, ListingEntry> result = new LinkedHashMap<>();
        var server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return result;
        CompoundTag listings = player.getPersistentData().getCompound(STORAGE_KEY);
        for (String itemId : listings.getAllKeys()) {
            CompoundTag entry = listings.getCompound(itemId);
            ListTag stacks = entry.getList("stacks", 10);
            int quantity = 0;
            String name = itemId;
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack stack = ItemStack.parse(player.registryAccess(), stacks.getCompound(i))
                        .orElseThrow(() -> new IllegalStateException("unreadable listing; retained for recovery"));
                quantity = Math.addExact(quantity, stack.getCount());
                if (i == 0) name = stack.getHoverName().getString();
            }
            result.put(itemId, new ListingEntry(itemId, name, "player_listing", quantity, entry.getLong("createdAt")));
        }
        return result;
    }

    private static JsonArray extractShopArray(JsonElement backendResult) {
        if (backendResult == null || backendResult.isJsonNull()) {
            return new JsonArray();
        }

        if (backendResult.isJsonArray()) {
            JsonArray source = backendResult.getAsJsonArray();
            JsonArray copy = new JsonArray();
            for (JsonElement element : source) {
                copy.add(element == null ? null : element.deepCopy());
            }
            return copy;
        }

        if (backendResult.isJsonObject()) {
            JsonElement items = backendResult.getAsJsonObject().get("items");
            if (items != null && items.isJsonArray()) {
                JsonArray copy = new JsonArray();
                for (JsonElement element : items.getAsJsonArray()) {
                    copy.add(element == null ? null : element.deepCopy());
                }
                return copy;
            }
        }

        return new JsonArray();
    }

    private static ListingEntry getListingEntry(UUID playerUuid, String itemId) {
        return readListings(playerUuid).get(itemId);
    }

    private static void applyListingMetadata(JsonObject target, ListingEntry listing) {
        target.addProperty("playerListed", true);
        target.addProperty("listingQuantity", Math.max(0, listing.quantity));
        target.addProperty("listedAtEpochMillis", listing.createdAtEpochMillis);

        String itemName = readString(target, "itemName");
        if (itemName == null || itemName.isBlank()) {
            target.addProperty("itemName", listing.itemName);
        }

        String category = readString(target, "category");
        if (category == null || category.isBlank()) {
            target.addProperty("category", listing.category);
        }

        mergeReasonTag(target, "player_listing");
        String summary = readString(target, "pricingSummary");
        if (summary == null || summary.isBlank()) {
            target.addProperty("pricingSummary", "Registered by player. Cancel sell to return original items to inventory.");
        }
    }

    private static void mergeReasonTag(JsonObject target, String tag) {
        JsonArray tags = target.has("pricingReasonTags") && target.get("pricingReasonTags").isJsonArray()
                ? target.getAsJsonArray("pricingReasonTags")
                : new JsonArray();

        for (JsonElement value : tags) {
            if (value != null && value.isJsonPrimitive() && tag.equalsIgnoreCase(value.getAsString())) {
                target.add("pricingReasonTags", tags);
                return;
            }
        }
        tags.add(tag);
        target.add("pricingReasonTags", tags);
    }

    private static String readString(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ListingEntry(
            String itemId,
            String itemName,
            String category,
            int quantity,
            long createdAtEpochMillis
    ) {
        JsonObject toShopItemJson() {
            JsonObject json = new JsonObject();
            json.addProperty("itemId", itemId);
            json.addProperty("itemName", itemName);
            json.addProperty("category", category);
            json.addProperty("buyPrice", 0);
            json.addProperty("sellPrice", 0);
            json.addProperty("currentBuyPrice", 0);
            json.addProperty("currentSellPrice", 0);
            json.addProperty("pricingSummary", "Registered by player. Cancel sell to return original items to inventory.");

            JsonArray reasonTags = new JsonArray();
            reasonTags.add("player_listing");
            json.add("pricingReasonTags", reasonTags);

            json.addProperty("isActive", true);
            json.addProperty("playerListed", true);
            json.addProperty("listingQuantity", quantity);
            json.addProperty("listedAtEpochMillis", createdAtEpochMillis);
            return json;
        }
    }
}
