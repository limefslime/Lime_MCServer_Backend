package com.namanseul.farmingmod.client.ui.mail;

import org.jetbrains.annotations.Nullable;

public record MailClaimResultViewData(
        String mailId,
        boolean claimed,
        @Nullable Integer rewardAmount,
        @Nullable Double balanceAfter,
        String mailType,
        boolean hasReward,
        String rewardType,
        @Nullable String itemRewardItemId,
        @Nullable Integer itemRewardQuantity,
        @Nullable MailViewData mail
) {}
