package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonObject;
import java.util.List;

public record ShopItemViewData(
        String itemId,
        String itemName,
        String category,
        int buyPrice,
        int sellPrice,
        int currentBuyPrice,
        int currentSellPrice,
        String pricingSummary,
        List<String> pricingReasonTags,
        JsonObject activePricing,
        JsonObject pricingPreview,
        boolean active,
        boolean playerListed,
        int listingQuantity,
        int stockQuantity
) {
    public String listLabel() {
        String name = itemName == null || itemName.isBlank() ? itemId : itemName;
        return name + " [" + itemId + "]";
    }
}
