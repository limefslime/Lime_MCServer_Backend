package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerShopListingService {
    private static final ConcurrentMap<UUID, ConcurrentMap<String, ListingEntry>> LISTINGS = new ConcurrentHashMap<>();

    private PlayerShopListingService() {}

    public static JsonElement mergeShopList(UUID playerUuid, JsonElement backendResult) {
        JsonArray merged = extractShopArray(backendResult);
        ConcurrentMap<String, ListingEntry> playerListings = LISTINGS.get(playerUuid);
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

    public static JsonObject registerListing(UUID playerUuid, String itemId, String itemName, int quantity) {
        ConcurrentMap<String, ListingEntry> playerListings =
                LISTINGS.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
        ListingEntry existing = playerListings.get(itemId);

        int nextQuantity = quantity;
        long createdAtEpochMillis = System.currentTimeMillis();
        if (existing != null) {
            nextQuantity = Math.max(1, existing.quantity + quantity);
            createdAtEpochMillis = existing.createdAtEpochMillis;
        }

        ListingEntry updated = new ListingEntry(
                itemId,
                normalizeName(itemId, itemName),
                "player_listing",
                nextQuantity,
                createdAtEpochMillis
        );
        playerListings.put(itemId, updated);
        return updated.toShopItemJson();
    }

    public static JsonObject getListing(UUID playerUuid, String itemId) {
        ListingEntry entry = getListingEntry(playerUuid, itemId);
        return entry == null ? null : entry.toShopItemJson();
    }

    public static JsonObject removeListing(UUID playerUuid, String itemId) {
        ConcurrentMap<String, ListingEntry> playerListings = LISTINGS.get(playerUuid);
        if (playerListings == null) {
            return null;
        }

        ListingEntry removed = playerListings.remove(itemId);
        if (playerListings.isEmpty()) {
            LISTINGS.remove(playerUuid);
        }
        return removed == null ? null : removed.toShopItemJson();
    }

    public static int listingQuantity(UUID playerUuid, String itemId) {
        ListingEntry entry = getListingEntry(playerUuid, itemId);
        return entry == null ? 0 : entry.quantity;
    }

    public static JsonObject adjustListingQuantity(UUID playerUuid, String itemId, int delta) {
        if (delta == 0) {
            return getListing(playerUuid, itemId);
        }

        ConcurrentMap<String, ListingEntry> playerListings = LISTINGS.get(playerUuid);
        if (playerListings == null) {
            return null;
        }

        ListingEntry existing = playerListings.get(itemId);
        if (existing == null) {
            return null;
        }

        int nextQuantity = existing.quantity + delta;
        if (nextQuantity <= 0) {
            playerListings.remove(itemId);
            if (playerListings.isEmpty()) {
                LISTINGS.remove(playerUuid);
            }
            return null;
        }

        ListingEntry updated = new ListingEntry(
                existing.itemId,
                existing.itemName,
                existing.category,
                nextQuantity,
                existing.createdAtEpochMillis
        );
        playerListings.put(itemId, updated);
        return updated.toShopItemJson();
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

    private static String normalizeName(String itemId, String itemName) {
        if (itemName != null && !itemName.isBlank()) {
            return itemName;
        }
        if (itemId == null || itemId.isBlank()) {
            return "listed item";
        }
        return itemId;
    }

    private static ListingEntry getListingEntry(UUID playerUuid, String itemId) {
        ConcurrentMap<String, ListingEntry> playerListings = LISTINGS.get(playerUuid);
        if (playerListings == null) {
            return null;
        }
        return playerListings.get(itemId);
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
            target.addProperty("pricingSummary", "Registered by player. Cancel sell to return by mail.");
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
            json.addProperty("pricingSummary", "Registered by player. Cancel sell to return by mail.");

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
