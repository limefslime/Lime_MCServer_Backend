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
            lines.add(Component.literal("상품을 선택하세요."));
            return lines;
        }

        String itemName = (item.itemName() == null || item.itemName().isBlank()) ? item.itemId() : item.itemName();
        lines.add(Component.literal(itemName));
        lines.add(Component.literal("구매: " + item.currentBuyPrice()));
        lines.add(Component.literal("판매: " + item.currentSellPrice()));

        if (buyPreview != null) {
            lines.add(Component.literal("구매 수량 " + buyPreview.quantity() + ": " + buyPreview.netTotalPrice()));
            if (buyPreview.feeAmount() > 0) {
                lines.add(Component.literal("구매 수수료: " + buyPreview.feeAmount()));
            }
            if (Boolean.FALSE.equals(buyPreview.canAfford())) {
                lines.add(Component.literal("잔액이 부족합니다."));
            }
        }

        if (sellPreview != null) {
            lines.add(Component.literal("판매 수량 " + sellPreview.quantity() + ": " + sellPreview.netTotalPrice()));
            if (sellPreview.feeAmount() > 0) {
                lines.add(Component.literal("판매 수수료: " + sellPreview.feeAmount()));
            }
        }

        if (buyPreview == null && sellPreview == null) {
            lines.add(Component.literal(previewLoading
                    ? "견적을 확인하는 중…"
                    : "견적을 볼 수량을 입력하세요."));
        }

        int stock = Math.max(0, item.stockQuantity());
        lines.add(Component.literal("재고: " + stock));
        if (item.playerListed()) {
            lines.add(Component.literal("내 등록 수량: " + Math.max(1, item.listingQuantity())));
            lines.add(Component.literal("내 등록 개당 가격: " + item.listingUnitPrice()));
        }

        if (trade != null && item.itemId().equals(trade.itemId())) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "구매 완료" : "판매 완료";
            lines.add(Component.literal("최근 기록: " + action + " x" + trade.quantity()));
            lines.add(Component.literal("최근 정산액: " + trade.netTotalPrice()));
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
