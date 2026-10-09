package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public final class ShopActionPanelView {
    private ShopActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopItemViewData selectedItem,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData lastTrade,
            @Nullable String quantityError,
            @Nullable String statusMessage,
            boolean previewLoading,
            boolean tradeLoading
    ) {
        int contentWidth = Math.max(0, width);
        if (contentWidth <= 0 || height <= 0) {
            return;
        }

        int infoTop = y;
        int infoBottom = y + Math.max(0, height - 68);
        int lineY = infoTop;
        int labelWidth = Math.max(52, Math.min(92, contentWidth / 3));

        if (selectedItem == null) {
            UiTextRender.drawEllipsized(graphics, font, "목록에서 상품을 선택하세요.", x, lineY, contentWidth, 0xDDE6F9);
        } else {
            String itemName = selectedItem.itemName() == null || selectedItem.itemName().isBlank()
                    ? selectedItem.itemId()
                    : selectedItem.itemName();
            UiTextRender.drawEllipsized(graphics, font, itemName, x, lineY, contentWidth, 0xFFFFFF);
            lineY += 14;
            graphics.fill(x, lineY, x + contentWidth, lineY + 1, 0x995A6A8A);
            lineY += 6;

            drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "구매:", formatAmount(selectedItem.currentBuyPrice()), 0xC7D7F1, 0xEAF1FF);
            lineY += 12;
            drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "판매:", formatAmount(selectedItem.currentSellPrice()), 0xC7D7F1, 0xEAF1FF);
            lineY += 14;

            if (buyPreview != null) {
                drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "구매 합계:", formatAmount(buyPreview.netTotalPrice()), 0xC7D7F1, 0xEAF1FF);
                lineY += 12;
                if (buyPreview.feeAmount() > 0 && lineY <= infoBottom) {
                    drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "구매 수수료:", formatAmount(buyPreview.feeAmount()), 0xBFD0E8, 0xDDE6F9);
                    lineY += 12;
                }
            }

            if (sellPreview != null && lineY <= infoBottom) {
                drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "판매 합계:", formatAmount(sellPreview.netTotalPrice()), 0xC7D7F1, 0xEAF1FF);
                lineY += 12;
                if (sellPreview.feeAmount() > 0 && lineY <= infoBottom) {
                    drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "판매 수수료:", formatAmount(sellPreview.feeAmount()), 0xBFD0E8, 0xDDE6F9);
                    lineY += 12;
                }
            }

            if (selectedItem.playerListed() && lineY <= infoBottom) {
                drawLine(graphics, font, x, lineY, contentWidth, labelWidth, "등록 수량:", formatAmount(Math.max(1, selectedItem.listingQuantity())), 0xBFD0E8, 0xDDE6F9);
                lineY += 12;
            }

            if (lastTrade != null
                    && selectedItem.itemId().equals(lastTrade.itemId())
                    && lineY <= infoBottom) {
                String action = "buy".equalsIgnoreCase(lastTrade.transactionType()) ? "구매 완료" : "판매 완료";
                UiTextRender.drawEllipsized(
                        graphics,
                        font,
                        action + " x" + Math.max(1, lastTrade.quantity()),
                        x,
                        lineY,
                        contentWidth,
                        0xBFD0E8
                );
            }
        }

        String state = null;
        int stateColor = 0xBFD0E8;
        if (quantityError != null && !quantityError.isBlank()) {
            state = quantityError;
            stateColor = 0xFF8E8E;
        } else if (tradeLoading) {
            state = "거래를 처리하는 중…";
        } else if (previewLoading) {
            state = "견적을 갱신하는 중…";
        } else if (statusMessage != null && !statusMessage.isBlank()) {
            state = statusMessage;
            stateColor = 0xDDE6F9;
        }

        int messageY = Math.max(y + 4, infoBottom + 6);
        if (state != null) {
            UiTextRender.drawEllipsized(graphics, font, state, x, messageY, contentWidth, stateColor);
        } else if (selectedItem != null) {
            UiTextRender.drawEllipsized(graphics, font, "수량을 입력하고 구매 또는 판매를 선택하세요.", x, messageY, contentWidth, 0xBFD0E8);
        }
    }

    private static void drawLine(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int labelWidth,
            String label,
            String value,
            int labelColor,
            int valueColor
    ) {
        UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, labelColor, valueColor);
    }

    private static String formatAmount(int value) {
        return String.format("%,d", value);
    }
}
