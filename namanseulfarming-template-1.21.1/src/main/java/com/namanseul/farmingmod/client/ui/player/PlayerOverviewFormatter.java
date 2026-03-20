package com.namanseul.farmingmod.client.ui.player;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class PlayerOverviewFormatter {
    public static final String TAB_WALLET = "wallet";
    public static final String TAB_ACTIVITY = "activity";
    public static final String TAB_SUMMARY = "summary";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private PlayerOverviewFormatter() {}

    public static List<Component> buildListEntries(
            String tabId,
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.ActivitySnapshot activity,
            PlayerOverviewData.SummarySnapshot summary
    ) {
        return switch (tabId) {
            case TAB_WALLET -> buildWalletEntries(wallet, summary);
            case TAB_ACTIVITY -> buildActivityEntries(activity);
            case TAB_SUMMARY -> buildSummaryEntries(summary);
            default -> List.of();
        };
    }

    public static List<Component> buildDetailLines(
            String tabId,
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.ActivitySnapshot activity,
            PlayerOverviewData.SummarySnapshot summary,
            int selectedIndex
    ) {
        return switch (tabId) {
            case TAB_WALLET -> buildWalletDetail(wallet, summary);
            case TAB_ACTIVITY -> buildActivityDetail(activity, selectedIndex);
            case TAB_SUMMARY -> buildSummaryDetail(summary, selectedIndex);
            default -> List.of();
        };
    }

    public static List<Component> buildOverviewHighlights(
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.ActivitySnapshot activity,
            PlayerOverviewData.SummarySnapshot summary,
            boolean partial
    ) {
        if (!wallet.available() && !activity.available() && !summary.available()) {
            return List.of(Component.literal("Loading player overview..."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        int rewardCount = summary.unclaimedRewardCount();
        int recentCount = summary.available() ? summary.recentActivityCount() : activity.totalCount();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Balance: " + formatNumber(balance)));
        lines.add(Component.literal("Rewards to claim: " + rewardCount));
        lines.add(Component.literal("Recent actions: " + recentCount));
        if (partial) {
            lines.add(Component.literal("Some details are still updating."));
        }
        return lines;
    }

    private static List<Component> buildWalletEntries(
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.SummarySnapshot summary
    ) {
        if (!wallet.available() && !summary.available()) {
            return List.of(Component.literal("Balance is loading..."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        int rewardCount = summary.unclaimedRewardCount();

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Available coins: " + formatNumber(balance)));
        if (rewardCount > 0) {
            entries.add(Component.literal("Rewards to claim: " + rewardCount));
        }
        return entries;
    }

    private static List<Component> buildWalletDetail(
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.SummarySnapshot summary
    ) {
        if (!wallet.available() && !summary.available()) {
            return List.of(Component.literal("Wallet details are not ready yet."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        int rewardCount = summary.unclaimedRewardCount();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Spendable now: " + formatNumber(balance)));
        if (rewardCount > 0) {
            lines.add(Component.literal("Claim " + rewardCount + " rewards to increase funds."));
        }
        return lines;
    }

    private static List<Component> buildActivityEntries(PlayerOverviewData.ActivitySnapshot activity) {
        if (!activity.available() || activity.items().isEmpty()) {
            return List.of(Component.literal("No recent activity."));
        }

        List<Component> entries = new ArrayList<>();
        for (PlayerOverviewData.ActivityItem item : activity.items()) {
            StringBuilder line = new StringBuilder();
            line.append(formatTime(item.occurredAtEpochMillis())).append(" | ").append(item.title());
            if (item.amountDelta() != 0) {
                line.append(" | ").append(signedNumber(item.amountDelta()));
            }
            entries.add(Component.literal(line.toString()));
        }
        return entries;
    }

    private static List<Component> buildActivityDetail(PlayerOverviewData.ActivitySnapshot activity, int selectedIndex) {
        if (!activity.available() || activity.items().isEmpty()) {
            return List.of(Component.literal("Select an activity to view details."));
        }

        int index = clampIndex(selectedIndex, activity.items().size());
        PlayerOverviewData.ActivityItem selected = activity.items().get(index);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(selected.title()));
        if (selected.amountDelta() != 0) {
            lines.add(Component.literal("Coin change: " + signedNumber(selected.amountDelta())));
        }
        if (!selected.description().isBlank()) {
            lines.add(Component.literal(selected.description()));
        }
        lines.add(Component.literal("Time: " + formatTime(selected.occurredAtEpochMillis())));
        return lines;
    }

    private static List<Component> buildSummaryEntries(PlayerOverviewData.SummarySnapshot summary) {
        if (!summary.available()) {
            return List.of(Component.literal("Summary is loading..."));
        }

        return List.of(
                Component.literal("Balance: " + formatNumber(summary.balance())),
                Component.literal("Rewards to claim: " + summary.unclaimedRewardCount()),
                Component.literal("Recent actions: " + summary.recentActivityCount())
        );
    }

    private static List<Component> buildSummaryDetail(PlayerOverviewData.SummarySnapshot summary, int selectedIndex) {
        if (!summary.available()) {
            return List.of(Component.literal("Summary details are not ready yet."));
        }

        return switch (clampIndex(selectedIndex, 3)) {
            case 0 -> List.of(
                    Component.literal("Available balance: " + formatNumber(summary.balance())),
                    Component.literal("Use coins for shop purchases and project contributions.")
            );
            case 1 -> List.of(
                    Component.literal("Rewards waiting: " + summary.unclaimedRewardCount()),
                    Component.literal("Claim rewards from Mail to add funds.")
            );
            default -> List.of(
                    Component.literal("Recent actions tracked: " + summary.recentActivityCount()),
                    Component.literal("Current focus region: " + safeText(summary.focusRegion(), "-"))
            );
        };
    }

    private static int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "--:--";
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
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

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
