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
        int recentCount = summary.available() ? summary.recentActivityCount() : activity.totalCount();
        String focus = summary.available() ? summary.focusRegion() : "-";

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Balance: " + formatNumber(balance)));
        lines.add(Component.literal("Rewards to claim: " + summary.unclaimedRewardCount()));
        lines.add(Component.literal("Recent actions: " + recentCount));
        if (!"-".equals(focus)) {
            lines.add(Component.literal("Current focus: " + focus));
        }
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
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Available coins: " + formatNumber(balance)));
        if (summary.unclaimedRewardCount() > 0) {
            entries.add(Component.literal("Rewards to claim: " + summary.unclaimedRewardCount()));
        }
        if (summary.activeProjectCount() > 0) {
            entries.add(Component.literal("Active projects: " + summary.activeProjectCount()));
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
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Spendable now: " + formatNumber(balance)));
        if (summary.unclaimedRewardCount() > 0) {
            lines.add(Component.literal("Claim " + summary.unclaimedRewardCount() + " reward mails to increase balance."));
        }
        if (summary.shopNetDelta() != 0) {
            lines.add(Component.literal("Recent market net: " + signedNumber(summary.shopNetDelta())));
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
            line.append(formatTime(item.occurredAtEpochMillis()))
                    .append(" | ")
                    .append(item.title());
            if (item.hasCoinChange()) {
                line.append(" | ").append(signedNumber(item.amountDelta()));
            }
            entries.add(Component.literal(line.toString()));
        }
        return entries;
    }

    private static List<Component> buildActivityDetail(PlayerOverviewData.ActivitySnapshot activity, int selectedIndex) {
        if (!activity.available() || activity.items().isEmpty()) {
            return List.of(Component.literal("Select an activity to see details."));
        }

        int index = clampIndex(selectedIndex, activity.items().size());
        PlayerOverviewData.ActivityItem selected = activity.items().get(index);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(selected.title()));
        if (selected.hasCoinChange()) {
            lines.add(Component.literal("Coin change: " + signedNumber(selected.amountDelta())));
        }
        if (selected.hasBalanceAfter()) {
            lines.add(Component.literal("Balance after action: " + formatNumber(selected.balanceAfter())));
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

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Balance: " + formatNumber(summary.balance())));
        entries.add(Component.literal("Rewards to claim: " + summary.unclaimedRewardCount()));
        entries.add(Component.literal("Recent actions: " + summary.recentActivityCount()));
        if (!"-".equals(summary.focusRegion())) {
            entries.add(Component.literal("Current focus: " + summary.focusRegion()));
        }
        entries.add(Component.literal("World activity: events " + summary.activeEventCount()
                + ", projects " + summary.activeProjectCount()));
        return entries;
    }

    private static List<Component> buildSummaryDetail(PlayerOverviewData.SummarySnapshot summary, int selectedIndex) {
        if (!summary.available()) {
            return List.of(Component.literal("Summary details are not ready yet."));
        }

        return switch (clampIndex(selectedIndex, 5)) {
            case 0 -> List.of(
                    Component.literal("Current coins: " + formatNumber(summary.balance())),
                    Component.literal("Use coins for shop purchases and investment.")
            );
            case 1 -> List.of(
                    Component.literal("Rewards waiting: " + summary.unclaimedRewardCount()),
                    Component.literal("Claimed reward total: " + formatNumber(summary.mailRewardTotal()))
            );
            case 2 -> List.of(
                    Component.literal("Tracked recent actions: " + summary.recentActivityCount()),
                    Component.literal("Market net: " + signedNumber(summary.shopNetDelta())),
                    Component.literal("Investment spend: " + formatNumber(summary.investSpendTotal()))
            );
            case 3 -> List.of(
                    Component.literal("Current focus region: " + summary.focusRegion()),
                    Component.literal("Dominant category: " + safeText(summary.dominantRegionCategory(), "-"))
            );
            default -> List.of(
                    Component.literal("Active events: " + summary.activeEventCount()),
                    Component.literal("Active project effects: " + summary.activeProjectEffectCount()),
                    Component.literal("Active projects: " + summary.activeProjectCount())
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
            return "--:--:--";
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
