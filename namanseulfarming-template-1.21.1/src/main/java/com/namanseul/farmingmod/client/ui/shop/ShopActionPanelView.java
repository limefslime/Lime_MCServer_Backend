package com.namanseul.farmingmod.client.ui.shop;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopActionPanelView {
    private ShopActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            @Nullable String quantityError,
            @Nullable String statusMessage,
            boolean previewLoading,
            boolean tradeLoading
    ) {
        int color = 0xDDE6F9;
        graphics.drawString(font, Component.translatable("screen.namanseulfarming.shop.quantity"), x, y, color, false);
        if (quantityError != null && !quantityError.isBlank()) {
            graphics.drawString(font, Component.literal(quantityError), x + 70, y, 0xFF7D7D, false);
        }

        int stateY = y + 40;
        if (previewLoading) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.shop.preview_loading"), x, stateY, color, false);
            stateY += 12;
        }
        if (tradeLoading) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.shop.trade_loading"), x, stateY, color, false);
            stateY += 12;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            graphics.drawString(font, Component.literal(statusMessage), x, stateY, 0xBFD0E8, false);
        }
    }
}
