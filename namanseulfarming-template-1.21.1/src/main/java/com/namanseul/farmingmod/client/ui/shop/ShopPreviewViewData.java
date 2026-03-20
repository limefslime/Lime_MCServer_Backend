package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public record ShopPreviewViewData(
        boolean preview,
        String transactionType,
        String itemId,
        int quantity,
        int unitPrice,
        int grossTotalPrice,
        int feeAmount,
        @Nullable Double feeRate,
        int netTotalPrice,
        int totalPrice,
        @Nullable Double balanceBefore,
        @Nullable Double balanceAfterPreview,
        @Nullable Boolean canAfford,
        @Nullable JsonObject pricing
) {
    public boolean isBuy() {
        return "buy".equalsIgnoreCase(transactionType);
    }

    public boolean isSell() {
        return "sell".equalsIgnoreCase(transactionType);
    }
}
