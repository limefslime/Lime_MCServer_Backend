package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class UiButton {
    private UiButton() {}

    public static Button create(Component label, int x, int y, int width, int height, Button.OnPress onPress) {
        return new FittedLabelButton(x, y, width, height, label, onPress);
    }

    private static final class FittedLabelButton extends Button {
        private Component fullLabel;

        private FittedLabelButton(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.fullLabel = label == null ? Component.empty() : label;
        }

        @Override
        public void setMessage(Component message) {
            super.setMessage(message);
            this.fullLabel = message == null ? Component.empty() : message;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            int maxWidth = Math.max(8, getWidth() - 10);
            String fitted = UiTextRender.ellipsize(font, fullLabel == null ? "" : fullLabel.getString(), maxWidth);
            if (fitted.isEmpty()) {
                return;
            }
            int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
            graphics.drawCenteredString(font, fitted, getX() + (getWidth() / 2), textY, color);
        }
    }
}
