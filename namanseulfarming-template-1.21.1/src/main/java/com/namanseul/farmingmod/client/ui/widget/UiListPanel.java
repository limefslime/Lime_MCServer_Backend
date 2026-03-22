package com.namanseul.farmingmod.client.ui.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class UiListPanel {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<Component> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    @Nullable
    private RowRenderer rowRenderer;

    public UiListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 14);
    }

    public UiListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<Component> newEntries) {
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

    public Component selectedEntry() {
        if (entries.isEmpty()) {
            return Component.empty();
        }
        return entries.get(Math.max(0, Math.min(selectedIndex, entries.size() - 1)));
    }

    public void setRowRenderer(@Nullable RowRenderer renderer) {
        this.rowRenderer = renderer;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x99202633);
        graphics.fill(x, y, x + width, y + 1, 0xFF4D5E7D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4D5E7D);
        graphics.fill(x, y, x + 1, y + height, 0xFF4D5E7D);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF4D5E7D);

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int baseY = y + 4 - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight < y || rowY > y + height) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY - 1, x + width - 2, rowY + rowHeight - 2, 0xAA3A4A6A);
            }
            int rowTextX = x + 6;
            int rowTextY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            int rowTextWidth = Math.max(0, width - 12);
            if (rowRenderer != null) {
                rowRenderer.render(
                        graphics,
                        font,
                        i,
                        entries.get(i),
                        rowTextX,
                        rowTextY,
                        rowTextWidth,
                        rowHeight,
                        i == selectedIndex
                );
                continue;
            }
            if (!renderStructuredEntry(graphics, font, entries.get(i).getString(), rowTextX, rowTextY, rowTextWidth)) {
                UiTextRender.drawEllipsized(graphics, font, entries.get(i).getString(), rowTextX, rowTextY, rowTextWidth, 0xFFFFFF);
            }
        }
        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }

        int relativeY = (int) mouseY - y - 4 + scrollOffset;
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
        int maxOffset = Math.max(0, entries.size() * rowHeight - (height - 8));
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean renderStructuredEntry(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String[] pipeColumns = Arrays.stream(text.split("\\|"))
                .map(String::trim)
                .toArray(String[]::new);
        if (pipeColumns.length == 2) {
            int rightWidth = Math.max(48, Math.min(120, width / 3));
            int leftWidth = Math.max(24, width - rightWidth - 8);
            UiTextRender.drawEllipsized(graphics, font, pipeColumns[0], x, y, leftWidth, 0xFFFFFF);
            UiTextRender.drawRightAligned(graphics, font, pipeColumns[1], x + width, y, rightWidth, 0xE8F0FF);
            return true;
        }
        if (pipeColumns.length >= 3) {
            String left = pipeColumns[0];
            String right = pipeColumns[pipeColumns.length - 1];
            String middle = String.join(" | ", Arrays.asList(pipeColumns).subList(1, pipeColumns.length - 1));

            int leftWidth = Math.max(36, Math.min(64, width / 4));
            int rightWidth = Math.max(40, Math.min(88, width / 4));
            int middleX = x + leftWidth + 6;
            int middleWidth = Math.max(24, width - leftWidth - rightWidth - 12);

            UiTextRender.drawEllipsized(graphics, font, left, x, y, leftWidth, 0xBFD0E8);
            UiTextRender.drawEllipsized(graphics, font, middle, middleX, y, middleWidth, 0xFFFFFF);
            UiTextRender.drawRightAligned(graphics, font, right, x + width, y, rightWidth, 0xE8F0FF);
            return true;
        }

        int colonIndex = text.indexOf(':');
        if (colonIndex > 0 && colonIndex < text.length() - 1) {
            String label = text.substring(0, colonIndex + 1).trim();
            String value = text.substring(colonIndex + 1).trim();
            if (label.length() <= 24 && !value.isBlank()) {
                int labelWidth = Math.max(40, Math.min(112, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    public interface RowRenderer {
        void render(
                GuiGraphics graphics,
                Font font,
                int rowIndex,
                Component entry,
                int rowX,
                int rowY,
                int rowWidth,
                int rowHeight,
                boolean selected
        );
    }
}
