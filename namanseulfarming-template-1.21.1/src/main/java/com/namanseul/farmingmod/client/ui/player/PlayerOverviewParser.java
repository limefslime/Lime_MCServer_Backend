package com.namanseul.farmingmod.client.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

        String playerId = readFirstString(wallet, "playerId", "player_id", "uuid");
        int balance = readFirstInt(wallet, 0, "balance", "coins", "amount");
        boolean available = !playerId.isBlank() || balance != 0 || !wallet.entrySet().isEmpty();
        return new PlayerOverviewData.WalletSnapshot(
                playerId,
                balance,
                available
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
            String category = normalizeKey(readFirstString(item, "category", "type", "sourceCategory"));
            String action = normalizeKey(readFirstString(item, "action", "event", "kind"));
            parsedItems.add(new PlayerOverviewData.ActivityItem(
                    buildActivityTitle(item, category, action),
                    buildActivityDescription(item, category, action),
                    readFirstInt(item, 0, "amountDelta", "delta", "amount"),
                    readFirstInt(
                            item,
                            PlayerOverviewData.ActivityItem.UNKNOWN_BALANCE_AFTER,
                            "balanceAfter",
                            "afterBalance"
                    ),
                    readFirstLong(item, 0L, "occurredAtEpochMillis", "occurredAt", "timestamp")
            ));
        }

        parsedItems.sort(Comparator.comparingLong(PlayerOverviewData.ActivityItem::occurredAtEpochMillis).reversed());
        int totalCount = readFirstInt(activity, parsedItems.size(), "count", "totalCount", "total");
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
                readFirstInt(summary, 0, "balance", "coins"),
                readFirstInt(mail, 0, "unclaimedRewardCount", "unclaimedCount"),
                readFirstInt(activity, 0, "recentCount", "recentActivityCount"),
                readFirstString(status, "currentFocusRegion", "focusRegion"),
                readFirstInt(status, 0, "activeEventCount", "eventCount"),
                readFirstInt(status, 0, "activeProjectEffectCount", "projectEffectCount"),
                readFirstInt(invest, 0, "activeProjectCount", "projectCount"),
                readFirstInt(activity, 0, "shopDelta"),
                readFirstInt(activity, 0, "investSpendTotal", "investSpent"),
                readFirstInt(activity, 0, "mailRewardTotal", "mailRewards"),
                readFirstString(status, "dominantRegionCategory", "dominantCategory"),
                true
        );
    }

    private static String buildActivityTitle(JsonObject item, String category, String action) {
        String title = readFirstString(item, "title", "label");
        if (!title.isBlank()) {
            return title;
        }

        if ("shop".equals(category)) {
            return switch (action) {
                case "buy" -> "Bought Item";
                case "sell" -> "Sold Item";
                case "register" -> "Listed Item for Sale";
                case "cancel_sell" -> "Canceled Listing";
                default -> "Shop Update";
            };
        }

        if ("invest".equals(category)) {
            return "Project Contribution";
        }

        if ("mail".equals(category)) {
            return "Mail Reward";
        }

        if (!action.isBlank()) {
            return humanizeKey(action);
        }
        return "Recent Activity";
    }

    private static String buildActivityDescription(JsonObject item, String category, String action) {
        String description = readFirstString(item, "description", "note", "message");
        if (!description.isBlank()) {
            return description;
        }

        String itemName = readFirstString(item, "itemName", "item_name", "itemId");
        if ("shop".equals(category)) {
            if (!itemName.isBlank()) {
                return "Item: " + itemName;
            }
            return switch (action) {
                case "buy" -> "Purchased from market";
                case "sell" -> "Sold to market";
                case "register" -> "Added to sell list";
                case "cancel_sell" -> "Removed from sell list";
                default -> "";
            };
        }

        if ("invest".equals(category)) {
            String projectId = readFirstString(item, "projectId", "project_id");
            return projectId.isBlank() ? "Investment progress update" : "Project: " + projectId;
        }

        if ("mail".equals(category)) {
            return "Reward added to wallet";
        }

        return "";
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

    private static String readFirstString(@Nullable JsonObject root, String... keys) {
        if (root == null) {
            return "";
        }

        for (String key : keys) {
            JsonElement value = root.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                String parsed = value.getAsString();
                if (parsed != null && !parsed.isBlank()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // keep searching
            }
        }
        return "";
    }

    private static int readFirstInt(@Nullable JsonObject root, int fallback, String... keys) {
        if (root == null) {
            return fallback;
        }

        for (String key : keys) {
            JsonElement value = root.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                return value.getAsInt();
            } catch (Exception ignored) {
                try {
                    return Math.round(value.getAsFloat());
                } catch (Exception ignoredAgain) {
                    // keep searching
                }
            }
        }
        return fallback;
    }

    private static long readFirstLong(@Nullable JsonObject root, long fallback, String... keys) {
        if (root == null) {
            return fallback;
        }

        for (String key : keys) {
            JsonElement value = root.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                return value.getAsLong();
            } catch (Exception ignored) {
                // keep searching
            }
        }
        return fallback;
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String humanizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Recent Activity";
        }

        String normalized = raw.replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return "Recent Activity";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
