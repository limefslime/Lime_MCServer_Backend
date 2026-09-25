package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public final class InvestStockJsonParser {
    private InvestStockJsonParser() {}

    public static List<InvestStockViewData> parseStockList(String json) {
        JsonElement parsed = parseElement(json);
        JsonArray stockArray;
        if (parsed.isJsonArray()) {
            stockArray = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            stockArray = readArray(parsed.getAsJsonObject(), "stocks");
        } else {
            stockArray = new JsonArray();
        }

        List<InvestStockViewData> result = new ArrayList<>();
        for (JsonElement stockElement : stockArray) {
            if (stockElement != null && stockElement.isJsonObject()) {
                result.add(parseStock(stockElement.getAsJsonObject()));
            }
        }
        return result;
    }

    public static InvestStockDetailViewData parseStockDetail(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("stock detail payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject stock = readObject(root, "stock");
        if (stock == null) {
            throw new IllegalArgumentException("stock section is missing");
        }

        JsonObject wallet = readObject(root, "wallet");
        int walletBalance = readInt(wallet, "balance", 0);
        return new InvestStockDetailViewData(parseStock(stock), walletBalance);
    }

    public static InvestTradeResultViewData parseTradeResult(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("trade payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject stock = readObject(root, "stock");
        InvestStockViewData stockData = stock == null ? null : parseStock(stock);

        return new InvestTradeResultViewData(
                readString(root, "side", "buy"),
                readString(root, "stockId", ""),
                readString(root, "stockName", ""),
                readInt(root, "quantity", 0),
                readInt(root, "executedPrice", 0),
                readInt(root, "totalPrice", 0),
                readInt(root, "walletBalanceAfter", 0),
                readInt(root, "realizedPnl", 0),
                stockData
        );
    }

    private static InvestStockViewData parseStock(JsonObject stock) {
        JsonObject holding = readObject(stock, "holding");
        return new InvestStockViewData(
                readString(stock, "id", ""),
                readString(stock, "name", "Unknown"),
                readInt(stock, "currentPrice", 0),
                readInt(stock, "previousPrice", 0),
                readInt(stock, "changeAmount", 0),
                readDouble(stock, "changeRate", 0.0),
                readInt(holding, "quantity", 0),
                readInt(holding, "avgBuyPrice", 0),
                readInt(holding, "investedCost", 0),
                readInt(holding, "marketValue", 0),
                readInt(holding, "unrealizedPnl", 0)
        );
    }

    private static JsonElement parseElement(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty invest payload");
        }
        return JsonParser.parseString(json);
    }

    private static JsonArray readArray(JsonObject root, String key) {
        if (root == null) {
            return new JsonArray();
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return new JsonArray();
        }
        return element.getAsJsonArray();
    }

    private static JsonObject readObject(JsonObject root, String key) {
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
        if (root == null) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        if (root == null) {
            return fallback;
        }
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

    private static double readDouble(JsonObject root, String key, double fallback) {
        if (root == null) {
            return fallback;
        }
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
}
