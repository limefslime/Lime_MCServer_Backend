package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public record UiMessageBanner(MessageType type, Component message) {
    public enum MessageType {
        INFO(0xAA224422, 0xFF66CC88),
        WARNING(0xAA4C3D19, 0xFFF0C060),
        ERROR(0xAA4A1A1A, 0xFFFF6B6B);

        private final int fillColor;
        private final int borderColor;

        MessageType(int fillColor, int borderColor) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
        }
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, type.fillColor);
        graphics.fill(x, y, x + width, y + 1, type.borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, type.borderColor);
        graphics.fill(x, y, x + 1, y + height, type.borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, type.borderColor);
        graphics.drawString(font, message, x + 6, y + (height - 8) / 2, 0xFFFFFF, false);
    }
}
