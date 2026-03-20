package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopDetailPanelView {
    private ShopDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopItemViewData item
    ) {
        List<Component> lines = buildLines(item);
        int lineY = y + 4;
        int maxY = y + height - 10;
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line, x + 6, lineY, 0xEAF1FF, false);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(@Nullable ShopItemViewData item) {
        List<Component> lines = new ArrayList<>();
        if (item == null) {
            lines.add(Component.translatable("screen.namanseulfarming.shop.no_selection"));
            return lines;
        }

        lines.add(Component.literal("itemId: " + item.itemId()));
        lines.add(Component.literal("itemName: " + item.itemName()));
        lines.add(Component.literal("category: " + item.category()));
        lines.add(Component.literal("active: " + item.active()));
        lines.add(Component.literal("stockQuantity: " + item.stockQuantity()));
        if (item.playerListed()) {
            lines.add(Component.literal("playerListed: true"));
            lines.add(Component.literal("listedQuantity: " + item.listingQuantity()));
            lines.add(Component.literal("Cancel Sell to return this item by mail."));
        }
        lines.add(Component.literal("buy price: " + item.currentBuyPrice() + " (base " + item.buyPrice() + ")"));
        lines.add(Component.literal("sell price: " + item.currentSellPrice() + " (base " + item.sellPrice() + ")"));

        if (item.pricingSummary() != null && !item.pricingSummary().isBlank()) {
            lines.add(Component.literal("pricingSummary: " + item.pricingSummary()));
        }
        if (!item.pricingReasonTags().isEmpty()) {
            lines.add(Component.literal("pricingReasonTags: " + String.join(", ", item.pricingReasonTags())));
        }

        JsonObject activePricing = item.activePricing() != null ? item.activePricing() : item.pricingPreview();
        JsonObject buyPricing = readObject(activePricing, "buy");
        if (buyPricing != null) {
            lines.add(Component.literal("buy totalMultiplier: " + readNumber(buyPricing, "totalMultiplier", 1.0)));
            appendModifierLines(lines, buyPricing);
        }

        JsonObject sellPricing = readObject(activePricing, "sell");
        if (sellPricing != null) {
            lines.add(Component.literal("sell totalMultiplier: " + readNumber(sellPricing, "totalMultiplier", 1.0)));
            appendModifierLines(lines, sellPricing);
        }

        return lines;
    }

    private static void appendModifierLines(List<Component> lines, JsonObject pricing) {
        JsonObject fee = readObject(pricing, "fee");
        if (fee != null) {
            lines.add(Component.literal("fee: applied=" + readBoolean(fee, "applied", false)
                    + " rate=" + readNumber(fee, "rate", 0.0)
                    + " amount=" + readInt(fee, "amount", 0)));
        }

        JsonObject modifiers = readObject(pricing, "modifiers");
        if (modifiers == null) {
            return;
        }

        JsonObject focus = readObject(modifiers, "focus");
        if (focus != null) {
            lines.add(Component.literal("focus: applied=" + readBoolean(focus, "applied", false)
                    + " mult=" + readNumber(focus, "multiplier", 1.0)
                    + " target=" + readString(focus, "target", "-")));
        }

        JsonObject projectEffects = readObject(modifiers, "projectEffects");
        if (projectEffects != null) {
            lines.add(Component.literal("projectEffects: applied=" + readBoolean(projectEffects, "applied", false)
                    + " count=" + readInt(projectEffects, "count", 0)
                    + " mult=" + readNumber(projectEffects, "multiplier", 1.0)));
        }

        JsonObject events = readObject(modifiers, "events");
        if (events != null) {
            lines.add(Component.literal("events: applied=" + readBoolean(events, "applied", false)
                    + " count=" + readInt(events, "count", 0)
                    + " mult=" + readNumber(events, "multiplier", 1.0)));
        }
    }

    @Nullable
    private static JsonObject readObject(@Nullable JsonObject root, String key) {
        if (root == null) {
            return null;
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double readNumber(JsonObject root, String key, double fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
