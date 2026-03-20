package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public record ShopTradeViewData(
        String transactionType,
        String itemId,
        int quantity,
        int unitPrice,
        int grossTotalPrice,
        int feeAmount,
        @Nullable Double feeRate,
        int netTotalPrice,
        int totalPrice,
        @Nullable Double balanceAfter,
        @Nullable JsonObject pricing
) {}
