package com.namanseul.farmingmod.client.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class PlayerOverviewParser {
    private PlayerOverviewParser() {}

    public static PlayerOverviewData parseOverview(String json) {
        JsonObject root = parseRootObject(json);
        PlayerOverviewData.WalletSnapshot wallet = parseWalletObject(readObject(root, "wallet"));
        PlayerOverviewData.ActivitySnapshot activity = parseActivityObject(readObject(root, "activity"));
        PlayerOverviewData.SummarySnapshot summary = parseSummaryObject(readObject(root, "summary"));
        boolean partial = readBoolean(root, "partial");
        return new PlayerOverviewData(wallet, activity, summary, partial);
    }

    public static PlayerOverviewData.WalletSnapshot parseWallet(String json) {
        return parseWalletObject(parseRootObject(json));
    }

    public static PlayerOverviewData.ActivitySnapshot parseActivity(String json) {
        return parseActivityObject(parseRootObject(json));
    }

    public static PlayerOverviewData.SummarySnapshot parseSummary(String json) {
        return parseSummaryObject(parseRootObject(json));
    }

    private static PlayerOverviewData.WalletSnapshot parseWalletObject(@Nullable JsonObject wallet) {
        if (wallet == null || wallet.entrySet().isEmpty()) {
            return PlayerOverviewData.WalletSnapshot.empty();
        }

        return new PlayerOverviewData.WalletSnapshot(
                readString(wallet, "playerId", ""),
                readInt(wallet, "balance", 0),
                true
        );
    }

    private static PlayerOverviewData.ActivitySnapshot parseActivityObject(@Nullable JsonObject activity) {
        if (activity == null) {
            return PlayerOverviewData.ActivitySnapshot.empty();
        }

        JsonArray items = readArray(activity, "items");
        List<PlayerOverviewData.ActivityItem> parsedItems = new ArrayList<>();
        for (JsonElement itemElement : items) {
            if (itemElement == null || !itemElement.isJsonObject()) {
                continue;
            }

            JsonObject item = itemElement.getAsJsonObject();
            parsedItems.add(new PlayerOverviewData.ActivityItem(
                    buildActivityTitle(item),
                    buildActivityDescription(item),
                    readInt(item, "amountDelta", 0),
                    readInt(item, "balanceAfter", PlayerOverviewData.ActivityItem.UNKNOWN_BALANCE_AFTER),
                    readLong(item, "occurredAtEpochMillis", 0L)
            ));
        }

        parsedItems.sort(Comparator.comparingLong(PlayerOverviewData.ActivityItem::occurredAtEpochMillis).reversed());
        int totalCount = readInt(activity, "count", parsedItems.size());
        boolean available = !parsedItems.isEmpty() || totalCount > 0 || !activity.entrySet().isEmpty();
        return new PlayerOverviewData.ActivitySnapshot(parsedItems, totalCount, available);
    }

    private static PlayerOverviewData.SummarySnapshot parseSummaryObject(@Nullable JsonObject summary) {
        if (summary == null || summary.entrySet().isEmpty()) {
            return PlayerOverviewData.SummarySnapshot.empty();
        }

        JsonObject mail = readObject(summary, "mail");
        JsonObject activity = readObject(summary, "activity");
        JsonObject invest = readObject(summary, "invest");
        JsonObject status = readObject(summary, "status");

        return new PlayerOverviewData.SummarySnapshot(
                readInt(summary, "balance", 0),
                readInt(mail, "unclaimedRewardCount", 0),
                readInt(activity, "recentCount", 0),
                readString(status, "currentFocusRegion", "-"),
                readInt(status, "activeEventCount", 0),
                readInt(status, "activeProjectEffectCount", 0),
                readInt(invest, "activeProjectCount", 0),
                readInt(activity, "shopDelta", 0),
                readInt(activity, "investSpendTotal", 0),
                readInt(activity, "mailRewardTotal", 0),
                readString(status, "dominantRegionCategory", ""),
                true
        );
    }

    private static String buildActivityTitle(JsonObject item) {
        String title = readString(item, "title", "");
        if (!title.isBlank()) {
            return title;
        }

        String category = readString(item, "category", "").toLowerCase();
        String action = readString(item, "action", "").toLowerCase();

        if ("shop".equals(category)) {
            return switch (action) {
                case "buy" -> "Shop Purchase";
                case "sell" -> "Shop Sale";
                case "register" -> "Sell Listing Added";
                case "cancel_sell" -> "Sell Listing Canceled";
                default -> "Shop Activity";
            };
        }

        if ("invest".equals(category)) {
            return "Project Contribution";
        }

        if ("mail".equals(category)) {
            return "Mail Reward Claimed";
        }

        return "Activity";
    }

    private static String buildActivityDescription(JsonObject item) {
        String description = readString(item, "description", "");
        if (!description.isBlank()) {
            return description;
        }

        String category = readString(item, "category", "").toLowerCase();
        String source = readString(item, "source", "");

        if ("shop".equals(category)) {
            return "Market transaction";
        }
        if ("invest".equals(category)) {
            return "Investment transaction";
        }
        if ("mail".equals(category)) {
            return "Mailbox reward";
        }
        return source;
    }

    private static JsonObject parseRootObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("player payload is empty");
        }

        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("player payload is not an object");
        }
        return parsed.getAsJsonObject();
    }

    @Nullable
    private static JsonObject readObject(@Nullable JsonObject root, String key) {
        if (root == null) {
            return null;
        }

        JsonElement value = root.get(key);
        if (value == null || !value.isJsonObject()) {
            return null;
        }
        return value.getAsJsonObject();
    }

    private static JsonArray readArray(@Nullable JsonObject root, String key) {
        if (root == null) {
            return new JsonArray();
        }

        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) {
            return new JsonArray();
        }
        return value.getAsJsonArray();
    }

    private static boolean readBoolean(@Nullable JsonObject root, String key) {
        if (root == null) {
            return false;
        }

        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readString(@Nullable JsonObject root, String key, String fallback) {
        if (root == null) {
            return fallback;
        }

        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            String parsed = value.getAsString();
            return parsed == null || parsed.isBlank() ? fallback : parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(@Nullable JsonObject root, String key, int fallback) {
        if (root == null) {
            return fallback;
        }

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

    private static long readLong(@Nullable JsonObject root, String key, long fallback) {
        if (root == null) {
            return fallback;
        }

        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }

        try {
            return value.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
