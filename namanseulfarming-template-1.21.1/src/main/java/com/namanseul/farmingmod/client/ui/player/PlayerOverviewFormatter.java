package com.namanseul.farmingmod.client.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PlayerOverviewFormatter {
    public static final String TAB_WALLET = "wallet";
    public static final String TAB_ACTIVITY = "activity";
    public static final String TAB_SUMMARY = "summary";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private PlayerOverviewFormatter() {}

    public static List<Component> buildListEntries(
            String tabId,
            @Nullable JsonObject wallet,
            @Nullable JsonObject activity,
            @Nullable JsonObject summary
    ) {
        return switch (tabId) {
            case TAB_WALLET -> buildWalletEntries(wallet);
            case TAB_ACTIVITY -> buildActivityEntries(activity);
            case TAB_SUMMARY -> buildSummaryEntries(summary);
            default -> List.of();
        };
    }

    public static List<Component> buildDetailLines(
            String tabId,
            @Nullable JsonObject wallet,
            @Nullable JsonObject activity,
            @Nullable JsonObject summary,
            int selectedIndex
    ) {
        return switch (tabId) {
            case TAB_WALLET -> buildWalletDetail(wallet);
            case TAB_ACTIVITY -> buildActivityDetail(activity, selectedIndex);
            case TAB_SUMMARY -> buildSummaryDetail(summary, selectedIndex);
            default -> List.of();
        };
    }

    private static List<Component> buildWalletEntries(@Nullable JsonObject wallet) {
        if (wallet == null || wallet.entrySet().isEmpty()) {
            return List.of(Component.literal("wallet data is not loaded yet"));
        }

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Balance: " + formatNumber(readInt(wallet, "balance", 0))));
        entries.add(Component.literal("Player: " + readString(wallet, "playerId", "-")));
        return entries;
    }

    private static List<Component> buildWalletDetail(@Nullable JsonObject wallet) {
        if (wallet == null || wallet.entrySet().isEmpty()) {
            return List.of(Component.literal("wallet detail is not available"));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("playerId: " + readString(wallet, "playerId", "-")));
        lines.add(Component.literal("balance: " + formatNumber(readInt(wallet, "balance", 0))));
        return lines;
    }

    private static List<Component> buildActivityEntries(@Nullable JsonObject activity) {
        JsonArray items = readArray(activity, "items");
        if (items.isEmpty()) {
            return List.of(Component.literal("no recent activity"));
        }

        List<Component> entries = new ArrayList<>();
        for (JsonElement itemElement : items) {
            if (itemElement == null || !itemElement.isJsonObject()) {
                continue;
            }
            JsonObject item = itemElement.getAsJsonObject();
            String title = readString(item, "title", readString(item, "category", "activity"));
            int delta = readInt(item, "amountDelta", 0);
            long occurredAt = readLong(item, "occurredAtEpochMillis", 0L);
            String time = occurredAt > 0 ? TIME_FORMATTER.format(Instant.ofEpochMilli(occurredAt)) : "--:--:--";
            entries.add(Component.literal(time + " | " + title + " | " + signedNumber(delta)));
        }
        return entries.isEmpty() ? List.of(Component.literal("no recent activity")) : entries;
    }

    private static List<Component> buildActivityDetail(@Nullable JsonObject activity, int selectedIndex) {
        JsonArray items = readArray(activity, "items");
        if (items.isEmpty()) {
            return List.of(Component.literal("recent activity detail is unavailable"));
        }

        int index = Math.max(0, Math.min(selectedIndex, items.size() - 1));
        JsonObject item = items.get(index).getAsJsonObject();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("category: " + readString(item, "category", "-")));
        lines.add(Component.literal("action: " + readString(item, "action", "-")));
        lines.add(Component.literal("title: " + readString(item, "title", "-")));
        lines.add(Component.literal("amountDelta: " + signedNumber(readInt(item, "amountDelta", 0))));
        lines.add(Component.literal("balanceAfter: " + readInt(item, "balanceAfter", 0)));
        lines.add(Component.literal("description: " + readString(item, "description", "-")));
        lines.add(Component.literal("source: " + readString(item, "source", "-")));
        lines.add(Component.literal("itemId: " + readString(item, "itemId", "-")));
        lines.add(Component.literal("projectId: " + readString(item, "projectId", "-")));
        lines.add(Component.literal("mailId: " + readString(item, "mailId", "-")));
        return lines;
    }

    private static List<Component> buildSummaryEntries(@Nullable JsonObject summary) {
        if (summary == null || summary.entrySet().isEmpty()) {
            return List.of(Component.literal("summary data is not loaded yet"));
        }

        JsonObject mail = readObject(summary, "mail");
        JsonObject activity = readObject(summary, "activity");
        JsonObject status = readObject(summary, "status");

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Balance: " + formatNumber(readInt(summary, "balance", 0))));
        entries.add(Component.literal("Unclaimed Mail: " + readInt(mail, "unclaimedRewardCount", 0)));
        entries.add(Component.literal("Recent Activity: " + readInt(activity, "recentCount", 0)));
        entries.add(Component.literal("Current Focus: " + readString(status, "currentFocusRegion", "-")));
        return entries;
    }

    private static List<Component> buildSummaryDetail(@Nullable JsonObject summary, int selectedIndex) {
        if (summary == null || summary.entrySet().isEmpty()) {
            return List.of(Component.literal("summary detail is unavailable"));
        }

        JsonObject mail = readObject(summary, "mail");
        JsonObject activity = readObject(summary, "activity");
        JsonObject invest = readObject(summary, "invest");
        JsonObject status = readObject(summary, "status");

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("playerId: " + readString(summary, "playerId", "-")));
        lines.add(Component.literal("balance: " + formatNumber(readInt(summary, "balance", 0))));
        lines.add(Component.literal("mail.totalCount: " + readInt(mail, "totalCount", 0)));
        lines.add(Component.literal("mail.unclaimedRewardCount: " + readInt(mail, "unclaimedRewardCount", 0)));
        lines.add(Component.literal("mail.claimedRewardCount: " + readInt(mail, "claimedRewardCount", 0)));
        lines.add(Component.literal("mail.claimedRewardAmount: " + readInt(mail, "claimedRewardAmount", 0)));
        lines.add(Component.literal("activity.recentCount: " + readInt(activity, "recentCount", 0)));
        lines.add(Component.literal("activity.shopDelta: " + signedNumber(readInt(activity, "shopDelta", 0))));
        lines.add(Component.literal("activity.investSpendTotal: " + readInt(activity, "investSpendTotal", 0)));
        lines.add(Component.literal("activity.mailRewardTotal: " + readInt(activity, "mailRewardTotal", 0)));
        lines.add(Component.literal("invest.activeProjectCount: " + readInt(invest, "activeProjectCount", 0)));
        lines.add(Component.literal("status.currentFocusRegion: " + readString(status, "currentFocusRegion", "-")));
        lines.add(Component.literal("status.activeEventCount: " + readInt(status, "activeEventCount", 0)));
        lines.add(Component.literal("status.activeProjectEffectCount: " + readInt(status, "activeProjectEffectCount", 0)));
        lines.add(Component.literal("status.dominantRegionCategory: " + readString(status, "dominantRegionCategory", "-")));
        return lines;
    }

    private static JsonArray readArray(@Nullable JsonObject root, String key) {
        if (root == null) {
            return new JsonArray();
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return new JsonArray();
        }
        return element.getAsJsonArray();
    }

    private static JsonObject readObject(@Nullable JsonObject root, String key) {
        if (root == null) {
            return null;
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String readString(@Nullable JsonObject root, String key, String fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(@Nullable JsonObject root, String key, int fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(element.getAsFloat());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private static long readLong(@Nullable JsonObject root, String key, long fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String signedNumber(int value) {
        if (value > 0) {
            return "+" + formatNumber(value);
        }
        if (value < 0) {
            return "-" + formatNumber(Math.abs(value));
        }
        return "0";
    }

    private static String formatNumber(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }
}

