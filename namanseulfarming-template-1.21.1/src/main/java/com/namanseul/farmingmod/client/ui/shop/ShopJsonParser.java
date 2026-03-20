package com.namanseul.farmingmod.client.ui.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class ShopJsonParser {
    private ShopJsonParser() {}

    public static List<ShopItemViewData> parseItems(String json) {
        JsonElement parsed = parseElement(json);
        JsonArray array = null;
        if (parsed.isJsonArray()) {
            array = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            JsonElement items = parsed.getAsJsonObject().get("items");
            if (items != null && items.isJsonArray()) {
                array = items.getAsJsonArray();
            }
        }

        if (array == null) {
            return List.of();
        }

        List<ShopItemViewData> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                items.add(parseItemObject(element.getAsJsonObject()));
            }
        }
        return items;
    }

    public static ShopItemViewData parseItem(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("shop item payload is not an object");
        }
        return parseItemObject(parsed.getAsJsonObject());
    }

    public static ShopPreviewViewData parsePreview(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("shop preview payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        int totalPrice = readInt(root, "totalPrice", 0);
        return new ShopPreviewViewData(
                readBoolean(root, "preview", true),
                readString(root, "transactionType", "buy"),
                readString(root, "itemId", ""),
                readInt(root, "quantity", 0),
                readInt(root, "unitPrice", 0),
                readInt(root, "grossTotalPrice", totalPrice),
                readInt(root, "feeAmount", 0),
                readDouble(root, "feeRate"),
                readInt(root, "netTotalPrice", totalPrice),
                totalPrice,
                readDouble(root, "balanceBefore"),
                readDouble(root, "balanceAfterPreview"),
                readBooleanNullable(root, "canAfford"),
                readObject(root, "pricing")
        );
    }

    public static ShopTradeViewData parseTrade(String json, String transactionType) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("shop trade payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        String type = readString(root, "transactionType", transactionType);
        int totalPrice = readInt(root, "totalPrice", 0);
        return new ShopTradeViewData(
                type,
                readString(root, "itemId", ""),
                readInt(root, "quantity", 0),
                readInt(root, "unitPrice", 0),
                readInt(root, "grossTotalPrice", totalPrice),
                readInt(root, "feeAmount", 0),
                readDouble(root, "feeRate"),
                readInt(root, "netTotalPrice", totalPrice),
                totalPrice,
                readDouble(root, "balanceAfter"),
                readObject(root, "pricing")
        );
    }

    private static ShopItemViewData parseItemObject(JsonObject root) {
        int baseBuy = readInt(root, "buyPrice", 0);
        int baseSell = readInt(root, "sellPrice", 0);
        int currentBuy = readInt(root, "currentBuyPrice", baseBuy);
        int currentSell = readInt(root, "currentSellPrice", baseSell);

        return new ShopItemViewData(
                readString(root, "itemId", ""),
                readString(root, "itemName", ""),
                readString(root, "category", ""),
                baseBuy,
                baseSell,
                currentBuy,
                currentSell,
                readString(root, "pricingSummary", ""),
                readStringArray(root, "pricingReasonTags"),
                readObject(root, "activePricing"),
                readObject(root, "pricingPreview"),
                readBoolean(root, "isActive", true),
                readBoolean(root, "playerListed", false),
                readInt(root, "listingQuantity", 0),
                readInt(root, "stockQuantity", 0)
        );
    }

    private static JsonElement parseElement(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty shop payload");
        }
        return JsonParser.parseString(json);
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null ? fallback : value;
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
            try {
                return Math.round(element.getAsFloat());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    @Nullable
    private static Double readDouble(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (Exception ignored) {
            return null;
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

    @Nullable
    private static Boolean readBooleanNullable(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static List<String> readStringArray(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value != null && value.isJsonPrimitive()) {
                values.add(value.getAsString());
            }
        }
        return values;
    }
}
