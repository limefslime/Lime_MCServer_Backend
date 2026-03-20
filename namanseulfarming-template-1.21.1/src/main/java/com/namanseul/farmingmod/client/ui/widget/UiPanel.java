package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.GuiGraphics;

public record UiPanel(int x, int y, int width, int height) {
    public void render(GuiGraphics graphics) {
        render(graphics, 0xF0141821, 0xFF5A6A8A);
    }

    public void render(GuiGraphics graphics, int fillColor, int borderColor) {
        graphics.fill(x, y, x + width, y + height, fillColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }
}
