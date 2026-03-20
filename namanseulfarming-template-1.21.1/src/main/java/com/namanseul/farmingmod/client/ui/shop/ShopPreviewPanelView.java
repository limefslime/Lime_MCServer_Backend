package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopPreviewPanelView {
    private ShopPreviewPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = buildLines(buyPreview, sellPreview, trade);
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

    private static List<Component> buildLines(
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = new ArrayList<>();

        if (buyPreview == null && sellPreview == null) {
            lines.add(Component.translatable("screen.namanseulfarming.shop.preview_waiting"));
        } else {
            if (buyPreview != null) {
                lines.add(Component.literal("[Buy Preview] qty=" + buyPreview.quantity()
                        + " unit=" + buyPreview.unitPrice()
                        + " gross=" + buyPreview.grossTotalPrice()
                        + " fee=" + buyPreview.feeAmount()
                        + " net=" + buyPreview.netTotalPrice()
                        + " canAfford=" + boolOrDash(buyPreview.canAfford())));
                appendPricingLines(lines, buyPreview.pricing());
            }
            if (sellPreview != null) {
                lines.add(Component.literal("[Sell Preview] qty=" + sellPreview.quantity()
                        + " unit=" + sellPreview.unitPrice()
                        + " gross=" + sellPreview.grossTotalPrice()
                        + " fee=" + sellPreview.feeAmount()
                        + " net=" + sellPreview.netTotalPrice()
                        + " canAfford=" + boolOrDash(sellPreview.canAfford())));
                appendPricingLines(lines, sellPreview.pricing());
            }
        }

        if (trade != null) {
            lines.add(Component.literal("last trade: " + trade.transactionType()
                    + " qty=" + trade.quantity()
                    + " unit=" + trade.unitPrice()
                    + " gross=" + trade.grossTotalPrice()
                    + " fee=" + trade.feeAmount()
                    + " net=" + trade.netTotalPrice()));
            lines.add(Component.literal("balanceAfter: " + numberOrDash(trade.balanceAfter())));
        }

        return lines;
    }

    private static void appendPricingLines(List<Component> lines, @Nullable JsonObject pricing) {
        if (pricing == null) {
            return;
        }

        String pricingSummary = readString(pricing, "pricingSummary", "");
        if (!pricingSummary.isBlank()) {
            lines.add(Component.literal("pricingSummary: " + pricingSummary));
        }

        JsonElement reasonTags = pricing.get("pricingReasonTags");
        if (reasonTags != null && reasonTags.isJsonArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonElement tag : reasonTags.getAsJsonArray()) {
                if (tag != null && tag.isJsonPrimitive()) {
                    tags.add(tag.getAsString());
                }
            }
            if (!tags.isEmpty()) {
                lines.add(Component.literal("pricingReasonTags: " + String.join(", ", tags)));
            }
        }

        lines.add(Component.literal("totalMultiplier: " + readNumber(pricing, "totalMultiplier", 1.0)));
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
        appendModifier(lines, "focus", readObject(modifiers, "focus"));
        appendModifier(lines, "projectEffects", readObject(modifiers, "projectEffects"));
        appendModifier(lines, "events", readObject(modifiers, "events"));
    }

    private static void appendModifier(List<Component> lines, String name, @Nullable JsonObject modifier) {
        if (modifier == null) {
            return;
        }
        lines.add(Component.literal(name + ": applied=" + readBoolean(modifier, "applied", false)
                + " mult=" + readNumber(modifier, "multiplier", 1.0)
                + " count=" + readInt(modifier, "count", 0)));
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

    private static String numberOrDash(@Nullable Double value) {
        if (value == null) {
            return "-";
        }
        return String.format("%.0f", value);
    }

    private static String boolOrDash(@Nullable Boolean value) {
        if (value == null) {
            return "-";
        }
        return Boolean.toString(value);
    }
}
