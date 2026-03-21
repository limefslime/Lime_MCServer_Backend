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
        String state = null;
        int stateColor = 0xBFD0E8;
        if (quantityError != null && !quantityError.isBlank()) {
            state = quantityError;
            stateColor = 0xFF7D7D;
        } else if (tradeLoading) {
            state = "Processing trade...";
        } else if (previewLoading) {
            state = "Checking price...";
        } else if (statusMessage != null && !statusMessage.isBlank()) {
            state = statusMessage;
        }

        if (state != null) {
            graphics.drawString(font, Component.literal(state), x + 56, y, stateColor, false);
        }
    }
}
