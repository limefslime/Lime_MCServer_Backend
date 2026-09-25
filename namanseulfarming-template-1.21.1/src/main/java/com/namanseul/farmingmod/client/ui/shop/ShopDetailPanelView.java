package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, font, line.getString(), contentX, lineY, contentWidth);
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
            lines.add(Component.literal("Select an item."));
            return lines;
        }

        String itemName = (item.itemName() == null || item.itemName().isBlank()) ? item.itemId() : item.itemName();
        lines.add(Component.literal(itemName));
        lines.add(Component.literal("Buy: " + item.currentBuyPrice()));
        lines.add(Component.literal("Sell: " + item.currentSellPrice()));

        if (buyPreview != null) {
            lines.add(Component.literal("Buy x" + buyPreview.quantity() + ": " + buyPreview.netTotalPrice()));
            if (buyPreview.feeAmount() > 0) {
                lines.add(Component.literal("Buy Fee: " + buyPreview.feeAmount()));
            }
            if (Boolean.FALSE.equals(buyPreview.canAfford())) {
                lines.add(Component.literal("Not enough balance."));
            }
        }

        if (sellPreview != null) {
            lines.add(Component.literal("Sell x" + sellPreview.quantity() + ": " + sellPreview.netTotalPrice()));
            if (sellPreview.feeAmount() > 0) {
                lines.add(Component.literal("Sell Fee: " + sellPreview.feeAmount()));
            }
        }

        if (buyPreview == null && sellPreview == null) {
            lines.add(Component.literal(previewLoading
                    ? "Checking quote..."
                    : "Set quantity for quote."));
        }

        int stock = Math.max(0, item.stockQuantity());
        lines.add(Component.literal("Stock: " + stock));
        if (item.playerListed()) {
            lines.add(Component.literal("Listed by you: " + Math.max(1, item.listingQuantity())));
        }

        if (trade != null && item.itemId().equals(trade.itemId())) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "Bought" : "Sold";
            lines.add(Component.literal("Last: " + action + " x" + trade.quantity()));
            lines.add(Component.literal("Last Net: " + trade.netTotalPrice()));
        }

        return lines;
    }

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 20) {
                int labelWidth = Math.max(48, Math.min(108, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, line, x, y, width, 0xEAF1FF);
    }
}
