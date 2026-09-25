package com.namanseul.farmingmod.client.ui.invest;

public record InvestTradeResultViewData(
        String side,
        String stockId,
        String stockName,
        int quantity,
        int executedPrice,
        int totalPrice,
        int walletBalanceAfter,
        int realizedPnl,
        InvestStockViewData stock
) {
}
