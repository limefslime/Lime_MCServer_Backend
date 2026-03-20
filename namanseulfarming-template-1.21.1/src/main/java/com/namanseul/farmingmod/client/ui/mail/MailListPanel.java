package com.namanseul.farmingmod.client.ui.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class MailListPanel {
    private static final int HEADER_HEIGHT = 14;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<MailViewData> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public MailListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 22);
    }

    public MailListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<MailViewData> newEntries) {
        this.entries = new ArrayList<>(newEntries == null ? Collections.emptyList() : newEntries);
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (selectedIndex >= entries.size()) {
            selectedIndex = entries.size() - 1;
        }
        clampScroll();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        clampScroll();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x99202633);
        graphics.fill(x, y, x + width, y + 1, 0xFF4D5E7D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4D5E7D);
        graphics.fill(x, y, x + 1, y + height, 0xFF4D5E7D);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF4D5E7D);

        renderHeader(graphics, font);
        graphics.fill(x + 2, y + HEADER_HEIGHT, x + width - 2, y + HEADER_HEIGHT + 1, 0x995A6A8A);

        int viewTop = y + HEADER_HEIGHT + 2;
        int viewBottom = y + height - 2;
        graphics.enableScissor(x + 1, viewTop, x + width - 1, viewBottom);
        int baseY = viewTop + 2 - scrollOffset;

        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight <= viewTop || rowY >= viewBottom) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + rowHeight - 1, 0xAA3A4A6A);
            }

            MailViewData mail = entries.get(i);
            String titlePrefix = mail.recent() ? "[NEW] " : "";
            String titleText = clipToWidth(font, titlePrefix + safe(mail.title()), width - 12);
            String summaryText = buildSummaryLine(mail);

            graphics.drawString(font, titleText, x + 6, rowY + 2, 0xFFFFFF, false);
            graphics.drawString(font, clipToWidth(font, summaryText, width - 12), x + 6, rowY + 12, 0xBFD0E8, false);
        }

        graphics.disableScissor();
    }

    private static String buildSummaryLine(MailViewData mail) {
        String claimed = mail.claimed() ? "claimed" : "unclaimed";
        String reward;
        if (!mail.hasReward()) {
            reward = "reward:none";
        } else if ("item".equalsIgnoreCase(mail.rewardType())) {
            reward = "reward:item";
        } else {
            reward = "reward:" + (mail.rewardAmount() == null ? "gold" : mail.rewardAmount());
        }
        return safe(mail.mailType()) + " | " + reward + " | " + claimed;
    }

    private void renderHeader(GuiGraphics graphics, Font font) {
        graphics.drawString(font, Component.literal("Mail"), x + 6, y + 3, 0xD7E4FF, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }
        if (mouseY < y + HEADER_HEIGHT + 2) {
            return false;
        }

        int relativeY = (int) mouseY - (y + HEADER_HEIGHT + 4) + scrollOffset;
        int clickedIndex = relativeY / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < entries.size()) {
            selectedIndex = clickedIndex;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int step = Math.max(1, rowHeight - 2);
        scrollOffset -= (int) Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int viewportHeight = height - HEADER_HEIGHT - 6;
        int maxOffset = Math.max(0, entries.size() * rowHeight - viewportHeight);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String clipToWidth(Font font, String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        int length = value.length();
        while (length > 0) {
            String candidate = value.substring(0, length);
            if (font.width(candidate) + ellipsisWidth <= maxWidth) {
                return candidate + ellipsis;
            }
            length--;
        }
        return "";
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
