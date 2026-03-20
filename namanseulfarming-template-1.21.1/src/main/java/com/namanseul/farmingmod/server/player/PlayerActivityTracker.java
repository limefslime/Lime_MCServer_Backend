package com.namanseul.farmingmod.server.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.network.UiAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class PlayerActivityTracker {
    private static final int MAX_ENTRIES_PER_PLAYER = 40;
    private static final ConcurrentHashMap<UUID, ConcurrentLinkedDeque<JsonObject>> ACTIVITY_MAP = new ConcurrentHashMap<>();

    private PlayerActivityTracker() {}

    public static void recordShopTrade(UUID playerUuid, UiAction action, JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return;
        }
        JsonObject root = result.getAsJsonObject();

        boolean buy = action == UiAction.SHOP_BUY;
        String itemId = readString(root, "itemId", "unknown");
        int quantity = readInt(root, "quantity", 0);
        int totalPrice = readInt(root, "totalPrice", 0);
        int balanceAfter = readInt(root, "balanceAfter", 0);
        int amountDelta = buy ? -Math.max(totalPrice, 0) : Math.max(totalPrice, 0);

        JsonObject entry = new JsonObject();
        entry.addProperty("entryId", "shop:" + Instant.now().toEpochMilli() + ":" + itemId);
        entry.addProperty("occurredAtEpochMillis", Instant.now().toEpochMilli());
        entry.addProperty("category", "shop");
        entry.addProperty("action", buy ? "buy" : "sell");
        entry.addProperty("title", "Shop " + (buy ? "buy" : "sell"));
        entry.addProperty("itemId", itemId);
        entry.addProperty("quantity", quantity);
        entry.addProperty("amountDelta", amountDelta);
        entry.addProperty("balanceAfter", balanceAfter);
        entry.addProperty("description", itemId + " x" + quantity + " total " + totalPrice);
        entry.addProperty("source", "ui_live");
        append(playerUuid, entry);
    }

    public static void recordInvest(UUID playerUuid, JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return;
        }
        JsonObject root = result.getAsJsonObject();

        String projectId = readString(root, "projectId", "unknown");
        int invested = readInt(root, "invested", 0);
        int projectTotal = readInt(root, "projectTotal", 0);

        JsonObject entry = new JsonObject();
        entry.addProperty("entryId", "invest:" + Instant.now().toEpochMilli() + ":" + projectId);
        entry.addProperty("occurredAtEpochMillis", Instant.now().toEpochMilli());
        entry.addProperty("category", "invest");
        entry.addProperty("action", "contribute");
        entry.addProperty("title", "Project invest");
        entry.addProperty("projectId", projectId);
        entry.addProperty("amountDelta", -Math.max(invested, 0));
        entry.addProperty("invested", invested);
        entry.addProperty("projectTotal", projectTotal);
        entry.addProperty("description", projectId + " +" + invested + " (total " + projectTotal + ")");
        entry.addProperty("source", "ui_live");
        append(playerUuid, entry);
    }

    public static void recordMailClaim(UUID playerUuid, JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            return;
        }
        JsonObject root = result.getAsJsonObject();

        String mailId = readString(root, "mailId", "unknown");
        int rewardAmount = readInt(root, "rewardAmount", 0);
        int balanceAfter = readInt(root, "balanceAfter", 0);

        JsonObject entry = new JsonObject();
        entry.addProperty("entryId", "mail:" + Instant.now().toEpochMilli() + ":" + mailId);
        entry.addProperty("occurredAtEpochMillis", Instant.now().toEpochMilli());
        entry.addProperty("category", "mail");
        entry.addProperty("action", "claim");
        entry.addProperty("title", "Mail claim");
        entry.addProperty("mailId", mailId);
        entry.addProperty("amountDelta", Math.max(rewardAmount, 0));
        entry.addProperty("rewardAmount", rewardAmount);
        entry.addProperty("balanceAfter", balanceAfter);
        entry.addProperty("description", "claimed reward " + rewardAmount);
        entry.addProperty("source", "ui_live");
        append(playerUuid, entry);
    }

    public static JsonElement getRecent(UUID playerUuid, int limit) {
        ConcurrentLinkedDeque<JsonObject> deque = ACTIVITY_MAP.get(playerUuid);
        if (deque == null || deque.isEmpty()) {
            return new com.google.gson.JsonArray();
        }

        List<JsonObject> snapshot = new ArrayList<>(deque);
        snapshot.sort(Comparator.comparingLong(PlayerActivityTracker::readOccurredAt).reversed());
        int max = Math.max(1, limit);

        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (int i = 0; i < snapshot.size() && i < max; i++) {
            array.add(snapshot.get(i).deepCopy());
        }
        return array;
    }

    private static long readOccurredAt(JsonObject item) {
        JsonElement value = item.get("occurredAtEpochMillis");
        if (value == null || value.isJsonNull()) {
            return 0L;
        }
        try {
            return value.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static void append(UUID playerUuid, JsonObject entry) {
        ACTIVITY_MAP.compute(playerUuid, (ignored, deque) -> {
            ConcurrentLinkedDeque<JsonObject> next = deque == null ? new ConcurrentLinkedDeque<>() : deque;
            next.addFirst(entry);
            while (next.size() > MAX_ENTRIES_PER_PLAYER) {
                next.pollLast();
            }
            return next;
        });
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            String parsed = value.getAsString();
            if (parsed == null || parsed.isBlank()) {
                return fallback;
            }
            return parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(value.getAsFloat());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }
}

