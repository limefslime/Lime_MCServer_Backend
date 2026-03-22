package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopItemListPanel {
    private static final int HEADER_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_MARGIN = 2;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<ShopItemViewData> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private boolean draggingScrollbar;
    private int dragThumbOffsetY;

    public ShopItemListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 20);
    }

    public ShopItemListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<ShopItemViewData> newEntries) {
        this.entries = new ArrayList<>(newEntries == null ? Collections.emptyList() : newEntries);
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            draggingScrollbar = false;
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

        int viewTop = viewTop();
        int viewBottom = viewBottom();
        int contentRight = contentRight();

        graphics.enableScissor(x + 1, viewTop, x + width - 1, viewBottom);
        int baseY = viewTop + 2 - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight <= viewTop || rowY >= viewBottom) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY, contentRight + 1, rowY + rowHeight - 1, 0xAA3A4A6A);
            }
            renderRow(graphics, font, entries.get(i), rowY, contentRight);
        }
        graphics.disableScissor();

        renderScrollbar(graphics);
    }

    private void renderRow(GuiGraphics graphics, Font font, ShopItemViewData item, int rowY, int contentRight) {
        ItemStack iconStack = resolveIconStack(item.itemId());
        int iconY = rowY + Math.max(0, (rowHeight - 16) / 2);
        int textY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
        graphics.renderItem(iconStack, x + 4, iconY);

        String name = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
        int priceWidth = 54;
        int priceRight = contentRight;
        int nameStart = x + 24;
        int nameWidth = Math.max(32, priceRight - priceWidth - 8 - nameStart);
        UiTextRender.drawEllipsized(graphics, font, name, nameStart, textY, nameWidth, 0xFFFFFF);
        UiTextRender.drawRightAligned(
                graphics,
                font,
                formatAmount(item.currentBuyPrice()),
                priceRight,
                textY,
                priceWidth,
                0xE8F0FF
        );
    }

    private void renderHeader(GuiGraphics graphics, Font font) {
        int color = 0xD7E4FF;
        graphics.drawString(font, Component.literal("Item"), x + 6, y + 4, color, false);
        UiTextRender.drawRightAligned(graphics, font, "Price", contentRight(), y + 4, 54, color);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (!hasScrollbar()) {
            return;
        }

        int barX = scrollbarX();
        int top = viewTop();
        int bottom = viewBottom();
        int thumbY = thumbY();
        int thumbHeight = thumbHeight();

        graphics.fill(barX, top, barX + SCROLLBAR_WIDTH, bottom, 0x88334455);
        graphics.fill(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFF88A4D8);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }

        if (hasScrollbar() && isOnScrollbar(mouseX, mouseY)) {
            int currentThumbY = thumbY();
            int currentThumbHeight = thumbHeight();
            int intMouseY = (int) mouseY;
            if (intMouseY >= currentThumbY && intMouseY <= currentThumbY + currentThumbHeight) {
                draggingScrollbar = true;
                dragThumbOffsetY = intMouseY - currentThumbY;
            } else {
                int page = Math.max(rowHeight, viewHeight() - rowHeight);
                if (intMouseY < currentThumbY) {
                    scrollOffset = Math.max(0, scrollOffset - page);
                } else {
                    scrollOffset = Math.min(maxScrollOffset(), scrollOffset + page);
                }
            }
            clampScroll();
            return true;
        }

        if (mouseY < viewTop()) {
            return false;
        }

        int relativeY = (int) mouseY - (viewTop() + 2) + scrollOffset;
        int clickedIndex = relativeY / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < entries.size()) {
            selectedIndex = clickedIndex;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!draggingScrollbar || !hasScrollbar()) {
            return false;
        }

        int trackTop = viewTop();
        int trackBottom = viewBottom();
        int thumbHeight = thumbHeight();
        int thumbTopMin = trackTop;
        int thumbTopMax = Math.max(trackTop, trackBottom - thumbHeight);
        int wantedThumbTop = (int) mouseY - dragThumbOffsetY;
        int clampedThumbTop = Math.max(thumbTopMin, Math.min(wantedThumbTop, thumbTopMax));

        int trackRange = Math.max(1, (trackBottom - trackTop) - thumbHeight);
        double ratio = (double) (clampedThumbTop - trackTop) / trackRange;
        scrollOffset = (int) Math.round(ratio * maxScrollOffset());
        clampScroll();
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || !draggingScrollbar) {
            return false;
        }
        draggingScrollbar = false;
        return true;
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
        int maxOffset = maxScrollOffset();
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private int maxScrollOffset() {
        return Math.max(0, entries.size() * rowHeight - (viewHeight() - 4));
    }

    private boolean hasScrollbar() {
        return maxScrollOffset() > 0;
    }

    private int viewTop() {
        return y + HEADER_HEIGHT + 2;
    }

    private int viewBottom() {
        return y + height - 2;
    }

    private int viewHeight() {
        return Math.max(0, viewBottom() - viewTop());
    }

    private int scrollbarX() {
        return x + width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
    }

    private int contentRight() {
        if (!hasScrollbar()) {
            return x + width - 6;
        }
        return scrollbarX() - 4;
    }

    private int thumbHeight() {
        int trackHeight = Math.max(8, viewHeight());
        int totalContent = Math.max(1, entries.size() * rowHeight);
        int visibleContent = Math.max(1, viewHeight());
        int thumb = (int) Math.round((double) visibleContent / totalContent * trackHeight);
        return Math.max(16, Math.min(trackHeight, thumb));
    }

    private int thumbY() {
        int trackTop = viewTop();
        int trackHeight = Math.max(1, viewHeight());
        int thumbHeight = thumbHeight();
        int movable = Math.max(1, trackHeight - thumbHeight);
        int maxOffset = maxScrollOffset();
        if (maxOffset <= 0) {
            return trackTop;
        }
        double ratio = (double) scrollOffset / maxOffset;
        return trackTop + (int) Math.round(ratio * movable);
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isOnScrollbar(double mouseX, double mouseY) {
        int barX = scrollbarX();
        int top = viewTop();
        int bottom = viewBottom();
        return mouseX >= barX && mouseX <= barX + SCROLLBAR_WIDTH && mouseY >= top && mouseY <= bottom;
    }

    private static ItemStack resolveIconStack(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return new ItemStack(Items.BARRIER);
        }

        Item item = null;
        ResourceLocation direct = ResourceLocation.tryParse(itemId);
        if (direct != null) {
            item = BuiltInRegistries.ITEM.get(direct);
        }

        if (item == null || item == Items.AIR) {
            ResourceLocation namespaced = ResourceLocation.tryParse("minecraft:" + itemId);
            if (namespaced != null) {
                item = BuiltInRegistries.ITEM.get(namespaced);
            }
        }

        if (item == null || item == Items.AIR) {
            item = Items.BARRIER;
        }
        return new ItemStack(item);
    }

    private static String formatAmount(int value) {
        return String.format("%,d", value);
    }
}
