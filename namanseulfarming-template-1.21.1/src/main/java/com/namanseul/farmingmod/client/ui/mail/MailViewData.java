package com.namanseul.farmingmod.client.ui.mail;

import org.jetbrains.annotations.Nullable;

public record MailViewData(
        String id,
        String playerId,
        String title,
        String message,
        String mailType,
        boolean hasReward,
        boolean claimed,
        @Nullable Integer rewardAmount,
        String rewardType,
        @Nullable String itemRewardItemId,
        @Nullable Integer itemRewardQuantity,
        String createdAtText,
        long createdAtEpochMillis,
        @Nullable String claimedAtText,
        long claimedAtEpochMillis,
        boolean recent
) {}
