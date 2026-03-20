package com.namanseul.farmingmod.client.ui.player;

import java.util.List;

public record PlayerOverviewData(
        WalletSnapshot wallet,
        ActivitySnapshot activity,
        SummarySnapshot summary,
        boolean partial
) {
    public PlayerOverviewData {
        wallet = wallet == null ? WalletSnapshot.empty() : wallet;
        activity = activity == null ? ActivitySnapshot.empty() : activity;
        summary = summary == null ? SummarySnapshot.empty() : summary;
    }

    public static PlayerOverviewData empty() {
        return new PlayerOverviewData(
                WalletSnapshot.empty(),
                ActivitySnapshot.empty(),
                SummarySnapshot.empty(),
                false
        );
    }

    public boolean hasAnySnapshot() {
        return wallet.available() || activity.available() || summary.available();
    }

    public int primaryBalance() {
        return summary.available() ? summary.balance() : wallet.balance();
    }

    public int readyRewardCount() {
        return summary.unclaimedRewardCount();
    }

    public int visibleRecentActivityCount() {
        return summary.available() ? summary.recentActivityCount() : activity.totalCount();
    }

    public record WalletSnapshot(
            String playerId,
            int balance,
            boolean available
    ) {
        public WalletSnapshot {
            playerId = playerId == null ? "" : playerId;
        }

        public static WalletSnapshot empty() {
            return new WalletSnapshot("", 0, false);
        }
    }

    public record ActivitySnapshot(
            List<ActivityItem> items,
            int totalCount,
            boolean available
    ) {
        public ActivitySnapshot {
            items = items == null ? List.of() : List.copyOf(items);
            totalCount = Math.max(totalCount, items.size());
        }

        public static ActivitySnapshot empty() {
            return new ActivitySnapshot(List.of(), 0, false);
        }
    }

    public record ActivityItem(
            String title,
            String description,
            int amountDelta,
            int balanceAfter,
            long occurredAtEpochMillis
    ) {
        public static final int UNKNOWN_BALANCE_AFTER = Integer.MIN_VALUE;

        public ActivityItem {
            title = title == null || title.isBlank() ? "Activity" : title;
            description = description == null ? "" : description;
        }

        public boolean hasBalanceAfter() {
            return balanceAfter != UNKNOWN_BALANCE_AFTER;
        }

        public boolean hasCoinChange() {
            return amountDelta != 0;
        }
    }

    public record SummarySnapshot(
            int balance,
            int unclaimedRewardCount,
            int recentActivityCount,
            String focusRegion,
            int activeEventCount,
            int activeProjectEffectCount,
            int activeProjectCount,
            int shopNetDelta,
            int investSpendTotal,
            int mailRewardTotal,
            String dominantRegionCategory,
            boolean available
    ) {
        public SummarySnapshot {
            focusRegion = focusRegion == null || focusRegion.isBlank() ? "-" : focusRegion;
            dominantRegionCategory = dominantRegionCategory == null ? "" : dominantRegionCategory;
        }

        public static SummarySnapshot empty() {
            return new SummarySnapshot(0, 0, 0, "-", 0, 0, 0, 0, 0, 0, "", false);
        }
    }
}
