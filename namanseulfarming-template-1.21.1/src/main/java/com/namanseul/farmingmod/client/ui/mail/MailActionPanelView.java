package com.namanseul.farmingmod.client.ui.mail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailActionPanelView {
    private MailActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            @Nullable String statusMessage,
            boolean claimLoading
    ) {
        int color = 0xDDE6F9;
        if (claimLoading) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.mail.claim_loading"), x, y, color, false);
            y += 12;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            graphics.drawString(font, Component.literal(statusMessage), x, y, 0xBFD0E8, false);
        }
    }
}
