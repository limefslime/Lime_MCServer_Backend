package com.namanseul.farmingmod.client.ui.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class VillageJsonParser {
    private VillageJsonParser() {}

    public static VillageFundViewData parseFundData(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("village payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject fund = readObject(root, "villageFund");
        JsonObject contribution = readObject(root, "contribution");
        JsonObject wallet = readObject(root, "wallet");

        return new VillageFundViewData(
                readInt(fund, "level", 1),
                readInt(fund, "totalAmount", 0),
                readInt(fund, "nextLevelRequirement", 10000),
                readInt(fund, "remainingToNextLevel", 10000),
                readDouble(fund, "shopDiscountRate", 0.0),
                readInt(contribution, "totalContribution", 0),
                readInt(wallet, "balance", 0)
        );
    }

    public static int parseDonatedAmount(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            return 0;
        }
        return readInt(parsed.getAsJsonObject(), "donatedAmount", 0);
    }

    private static JsonElement parseElement(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty village payload");
        }
        return JsonParser.parseString(json);
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
