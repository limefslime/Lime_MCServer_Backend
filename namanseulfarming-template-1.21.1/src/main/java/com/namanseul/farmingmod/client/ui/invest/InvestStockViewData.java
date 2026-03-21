package com.namanseul.farmingmod.client.ui.invest;

public record InvestStockViewData(
        String stockId,
        String name,
        int currentPrice,
        int previousPrice,
        int changeAmount,
        double changeRate,
        int holdingQuantity,
        int avgBuyPrice,
        int investedCost,
        int marketValue,
        int unrealizedPnl
) {
    public String listLabel() {
        String changePrefix = changeAmount > 0 ? "+" : "";
        return name + "  " + currentPrice + " (" + changePrefix + changeAmount + ")";
    }
}
