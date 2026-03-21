package com.namanseul.farmingmod.client.ui.shop;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopPreviewPanelView {
    private ShopPreviewPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = buildLines(buyPreview, sellPreview, trade);
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

    public static List<Component> buildLines(
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = new ArrayList<>();
        if (buyPreview == null && sellPreview == null && trade == null) {
            lines.add(Component.literal("Select an item and quantity to view quote."));
            return lines;
        }

        if (buyPreview != null) {
            lines.add(Component.literal("Buy x" + buyPreview.quantity()
                    + " -> " + buyPreview.netTotalPrice()
                    + " (fee " + buyPreview.feeAmount() + ")"));
        }
        if (sellPreview != null) {
            lines.add(Component.literal("Sell x" + sellPreview.quantity()
                    + " -> " + sellPreview.netTotalPrice()
                    + " (fee " + sellPreview.feeAmount() + ")"));
        }
        if (trade != null) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "Bought" : "Sold";
            lines.add(Component.literal("Last trade: " + action + " x" + trade.quantity()));
        }

        return lines;
    }
}
