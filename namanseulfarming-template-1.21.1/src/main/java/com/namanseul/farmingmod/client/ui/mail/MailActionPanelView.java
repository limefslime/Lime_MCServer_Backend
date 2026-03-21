package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
            int width,
            @Nullable String statusMessage,
            boolean claimLoading
    ) {
        int contentWidth = Math.max(0, width);
        int color = 0xDDE6F9;
        if (claimLoading) {
            UiTextRender.drawEllipsized(
                    graphics,
                    font,
                    Component.translatable("screen.namanseulfarming.mail.claim_loading").getString(),
                    x,
                    y,
                    contentWidth,
                    color
            );
            y += 12;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            UiTextRender.drawEllipsized(graphics, font, statusMessage, x, y, contentWidth, 0xBFD0E8);
        }
    }
}
