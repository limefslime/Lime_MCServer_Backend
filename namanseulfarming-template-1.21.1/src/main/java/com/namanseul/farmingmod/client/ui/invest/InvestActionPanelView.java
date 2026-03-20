package com.namanseul.farmingmod.client.ui.invest;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestActionPanelView {
    private InvestActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            @Nullable String amountError,
            @Nullable String statusMessage,
            boolean investLoading
    ) {
        int color = 0xDDE6F9;
        graphics.drawString(font, Component.translatable("screen.namanseulfarming.invest.amount"), x, y, color, false);
        if (amountError != null && !amountError.isBlank()) {
            graphics.drawString(font, Component.literal(amountError), x + 58, y, 0xFF7D7D, false);
        }

        int lineY = y + 28;
        if (investLoading) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.invest.investing"), x, lineY, color, false);
            lineY += 12;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            graphics.drawString(font, Component.literal(statusMessage), x, lineY, 0xBFD0E8, false);
        }
    }
}
