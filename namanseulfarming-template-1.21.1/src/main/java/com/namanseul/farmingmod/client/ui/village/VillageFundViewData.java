package com.namanseul.farmingmod.client.ui.village;

public record VillageFundViewData(
        int level,
        int totalAmount,
        int nextLevelRequirement,
        int remainingToNextLevel,
        double shopDiscountRate,
        int playerContribution,
        int walletBalance
) {
}
