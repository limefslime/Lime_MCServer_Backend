package com.namanseul.farmingmod.client.ui.shop;

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
    private static final int HEADER_HEIGHT = 14;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<ShopItemViewData> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public ShopItemListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 18);
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

            ShopItemViewData item = entries.get(i);
            ItemStack iconStack = resolveIconStack(item.itemId());
            int iconY = rowY + Math.max(0, (rowHeight - 16) / 2);
            int textY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            graphics.renderItem(iconStack, x + 4, iconY);

            String name = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
            int stock = Math.max(0, item.stockQuantity());
            if (item.playerListed()) {
                name = "[L" + Math.max(1, item.listingQuantity()) + "/S" + stock + "] " + name;
            } else {
                name = "[S" + stock + "] " + name;
            }
            int itemColumnEnd = x + width - 86;
            String clippedName = clipToWidth(font, name, Math.max(24, itemColumnEnd - (x + 24)));
            graphics.drawString(font, clippedName, x + 24, textY, 0xFFFFFF, false);

            String buyText = Integer.toString(item.currentBuyPrice());
            String sellText = Integer.toString(item.currentSellPrice());
            int buyRight = x + width - 46;
            int sellRight = x + width - 8;
            graphics.drawString(font, buyText, buyRight - font.width(buyText), textY, 0xE8F0FF, false);
            graphics.drawString(font, sellText, sellRight - font.width(sellText), textY, 0xE8F0FF, false);
        }

        graphics.disableScissor();
    }

    private void renderHeader(GuiGraphics graphics, Font font) {
        int color = 0xD7E4FF;
        graphics.drawString(font, Component.literal("Item"), x + 6, y + 3, color, false);
        graphics.drawString(font, Component.literal("Buy"), x + width - 70, y + 3, color, false);
        graphics.drawString(font, Component.literal("Sell"), x + width - 34, y + 3, color, false);
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
}
