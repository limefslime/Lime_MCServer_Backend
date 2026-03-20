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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
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
            return List.of(Component.literal("Waiting for player overview..."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        int recentCount = summary.available() ? summary.recentActivityCount() : activity.totalCount();
        String focus = summary.available() ? summary.focusRegion() : "-";

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Balance: " + formatNumber(balance)));
        lines.add(Component.literal("Unclaimed rewards: " + summary.unclaimedRewardCount()));
        lines.add(Component.literal("Recent actions: " + recentCount));
        lines.add(Component.literal("Current focus: " + focus));
        if (partial) {
            lines.add(Component.literal("Some values are temporarily unavailable."));
        }
        return lines;
    }

    private static List<Component> buildWalletEntries(
            PlayerOverviewData.WalletSnapshot wallet,
            PlayerOverviewData.SummarySnapshot summary
    ) {
        if (!wallet.available() && !summary.available()) {
            return List.of(Component.literal("Balance information is loading..."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Available coins: " + formatNumber(balance)));
        if (summary.unclaimedRewardCount() > 0) {
            entries.add(Component.literal("Unclaimed rewards: " + summary.unclaimedRewardCount()));
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
            return List.of(Component.literal("No wallet details yet."));
        }

        int balance = summary.available() ? summary.balance() : wallet.balance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Spendable balance: " + formatNumber(balance)));
        lines.add(Component.literal("Use this balance in Shop and Investment tabs."));
        if (summary.unclaimedRewardCount() > 0) {
            lines.add(Component.literal("Claim " + summary.unclaimedRewardCount() + " reward mails for extra funds."));
        }
        if (summary.shopNetDelta() != 0) {
            lines.add(Component.literal("Recent shop net: " + signedNumber(summary.shopNetDelta())));
        }
        return lines;
    }

    private static List<Component> buildActivityEntries(PlayerOverviewData.ActivitySnapshot activity) {
        if (!activity.available() || activity.items().isEmpty()) {
            return List.of(Component.literal("No recent activity."));
        }

        List<Component> entries = new ArrayList<>();
        for (PlayerOverviewData.ActivityItem item : activity.items()) {
            entries.add(Component.literal(
                    formatTime(item.occurredAtEpochMillis())
                            + " | "
                            + item.title()
                            + " | "
                            + signedNumber(item.amountDelta())
            ));
        }
        return entries;
    }

    private static List<Component> buildActivityDetail(PlayerOverviewData.ActivitySnapshot activity, int selectedIndex) {
        if (!activity.available() || activity.items().isEmpty()) {
            return List.of(Component.literal("Activity detail will appear here."));
        }

        int index = clampIndex(selectedIndex, activity.items().size());
        PlayerOverviewData.ActivityItem selected = activity.items().get(index);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(selected.title()));
        lines.add(Component.literal("Coin change: " + signedNumber(selected.amountDelta())));
        if (selected.hasBalanceAfter()) {
            lines.add(Component.literal("Balance after action: " + formatNumber(selected.balanceAfter())));
        }
        lines.add(Component.literal("Recorded at: " + formatTime(selected.occurredAtEpochMillis())));
        if (!selected.description().isBlank()) {
            lines.add(Component.literal(selected.description()));
        }
        return lines;
    }

    private static List<Component> buildSummaryEntries(PlayerOverviewData.SummarySnapshot summary) {
        if (!summary.available()) {
            return List.of(Component.literal("Summary information is loading..."));
        }

        return List.of(
                Component.literal("Current focus: " + summary.focusRegion()),
                Component.literal("Unclaimed rewards: " + summary.unclaimedRewardCount()),
                Component.literal("Recent actions: " + summary.recentActivityCount()),
                Component.literal("Active events: " + summary.activeEventCount())
        );
    }

    private static List<Component> buildSummaryDetail(PlayerOverviewData.SummarySnapshot summary, int selectedIndex) {
        if (!summary.available()) {
            return List.of(Component.literal("Summary detail is not available yet."));
        }

        return switch (clampIndex(selectedIndex, 4)) {
            case 0 -> List.of(
                    Component.literal("Current focus region: " + summary.focusRegion()),
                    Component.literal("Dominant category: " + safeText(summary.dominantRegionCategory(), "-"))
            );
            case 1 -> List.of(
                    Component.literal("Unclaimed rewards: " + summary.unclaimedRewardCount()),
                    Component.literal("Total reward coins gained: " + formatNumber(summary.mailRewardTotal()))
            );
            case 2 -> List.of(
                    Component.literal("Recent actions tracked: " + summary.recentActivityCount()),
                    Component.literal("Shop net change: " + signedNumber(summary.shopNetDelta())),
                    Component.literal("Investment spend total: " + formatNumber(summary.investSpendTotal()))
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
