package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public final class UiTextRender {
    private static final String ELLIPSIS = "...";

    private UiTextRender() {}

    public static String ellipsize(Font font, @Nullable String value, int maxWidth) {
        if (value == null || value.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        String candidate = font.plainSubstrByWidth(value, maxWidth - ellipsisWidth);
        while (!candidate.isEmpty() && font.width(candidate + ELLIPSIS) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isEmpty()) {
            return "";
        }
        return candidate + ELLIPSIS;
    }

    public static void drawEllipsized(
            GuiGraphics graphics,
            Font font,
            @Nullable String value,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        String text = ellipsize(font, value, maxWidth);
        if (text.isEmpty()) {
            return;
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    public static void drawRightAligned(
            GuiGraphics graphics,
            Font font,
            @Nullable String value,
            int rightX,
            int y,
            int maxWidth,
            int color
    ) {
        String text = ellipsize(font, value, maxWidth);
        if (text.isEmpty()) {
            return;
        }
        int drawX = rightX - font.width(text);
        graphics.drawString(font, text, drawX, y, color, false);
    }

    public static void drawLabelValue(
            GuiGraphics graphics,
            Font font,
            @Nullable String label,
            @Nullable String value,
            int x,
            int y,
            int totalWidth,
            int labelWidth,
            int labelColor,
            int valueColor
    ) {
        if (totalWidth <= 0) {
            return;
        }
        int safeLabelWidth = Math.max(16, Math.min(labelWidth, Math.max(16, totalWidth - 10)));
        int valueWidth = Math.max(0, totalWidth - safeLabelWidth - 6);
        drawEllipsized(graphics, font, label, x, y, safeLabelWidth, labelColor);
        drawRightAligned(graphics, font, value, x + totalWidth, y, valueWidth, valueColor);
    }
}
