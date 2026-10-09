package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            UiTextRender.drawEllipsized(graphics, font, line.getString(), contentX, lineY, contentWidth, 0xEAF1FF);
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
            lines.add(Component.literal("상품과 수량을 선택하세요."));
            return lines;
        }

        if (buyPreview != null) {
            lines.add(Component.literal("구매 수량 " + buyPreview.quantity() + ": " + buyPreview.netTotalPrice()));
            if (buyPreview.feeAmount() > 0) {
                lines.add(Component.literal("구매 수수료: " + buyPreview.feeAmount()));
            }
        }
        if (sellPreview != null) {
            lines.add(Component.literal("판매 수량 " + sellPreview.quantity() + ": " + sellPreview.netTotalPrice()));
            if (sellPreview.feeAmount() > 0) {
                lines.add(Component.literal("판매 수수료: " + sellPreview.feeAmount()));
            }
        }
        if (trade != null) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "구매 완료" : "판매 완료";
            lines.add(Component.literal("최근 거래: " + action + " x" + trade.quantity()));
        }

        return lines;
    }
}
