package com.namanseul.farmingmod.client.ui.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopDetailPanelView {
    private ShopDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopItemViewData item,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            boolean previewLoading,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = buildLines(item, buyPreview, sellPreview, previewLoading, trade);
        int lineY = y + 4;
        int maxY = y + height - 10;
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line, x + 6, lineY, 0xEAF1FF, false);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(
            @Nullable ShopItemViewData item,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            boolean previewLoading,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = new ArrayList<>();
        if (item == null) {
            lines.add(Component.literal("Select an item from the list."));
            return lines;
        }

        String itemName = (item.itemName() == null || item.itemName().isBlank()) ? item.itemId() : item.itemName();
        lines.add(Component.literal(itemName));
        lines.add(Component.literal("Price now: Buy " + item.currentBuyPrice() + " / Sell " + item.currentSellPrice()));

        if (buyPreview != null) {
            lines.add(Component.literal("Buy x" + buyPreview.quantity()
                    + ": Pay " + buyPreview.netTotalPrice()
                    + " (Fee " + buyPreview.feeAmount() + ")"));
            if (Boolean.FALSE.equals(buyPreview.canAfford())) {
                lines.add(Component.literal("Cannot buy: balance is too low."));
            }
        }

        if (sellPreview != null) {
            lines.add(Component.literal("Sell x" + sellPreview.quantity()
                    + ": Receive " + sellPreview.netTotalPrice()
                    + " (Fee " + sellPreview.feeAmount() + ")"));
        }

        if (buyPreview == null && sellPreview == null) {
            lines.add(Component.literal(previewLoading
                    ? "Checking latest quote..."
                    : "Adjust quantity to view expected result."));
        }

        int stock = Math.max(0, item.stockQuantity());
        lines.add(Component.literal("Stock: " + stock));
        if (item.playerListed()) {
            lines.add(Component.literal("Listed by you: " + Math.max(1, item.listingQuantity())));
        }

        if (trade != null && item.itemId().equals(trade.itemId())) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "Bought" : "Sold";
            lines.add(Component.literal("Recent trade: " + action + " x" + trade.quantity()
                    + " (Net " + trade.netTotalPrice() + ")"));
        }

        if (item.playerListed()) {
            lines.add(Component.literal("Use Cancel Sell to remove your listing."));
        }

        return lines;
    }
}
