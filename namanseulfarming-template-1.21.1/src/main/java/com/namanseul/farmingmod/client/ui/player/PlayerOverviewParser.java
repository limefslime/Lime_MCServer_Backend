package com.namanseul.farmingmod.client.ui.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

public final class PlayerOverviewParser {
    private PlayerOverviewParser() {}

    public static PlayerOverviewData parseOverview(String json) {
        JsonObject root = parseRootObject(json);
        JsonObject wallet = readObject(root, "wallet");
        JsonObject activity = readObject(root, "activity");
        JsonObject summary = readObject(root, "summary");
        boolean partial = readBoolean(root, "partial");
        List<String> partialNotes = readStringList(root, "partialNotes");
        return new PlayerOverviewData(
                wallet == null ? new JsonObject() : wallet,
                activity == null ? defaultActivityObject() : activity,
                summary == null ? new JsonObject() : summary,
                partial,
                partialNotes
        );
    }

    public static JsonObject parseWallet(String json) {
        return parseRootObject(json);
    }

    public static JsonObject parseActivity(String json) {
        JsonObject activity = parseRootObject(json);
        if (!activity.has("items") || !activity.get("items").isJsonArray()) {
            activity.add("items", new JsonArray());
        }
        return activity;
    }

    public static JsonObject parseSummary(String json) {
        return parseRootObject(json);
    }

    private static JsonObject parseRootObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("player overview payload is empty");
        }
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("player overview payload is not an object");
        }
        return parsed.getAsJsonObject();
    }

    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonObject()) {
            return null;
        }
        return value.getAsJsonObject();
    }

    private static boolean readBoolean(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<String> readStringList(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (JsonElement entry : value.getAsJsonArray()) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }
            try {
                String parsed = entry.getAsString();
                if (parsed != null && !parsed.isBlank()) {
                    items.add(parsed);
                }
            } catch (Exception ignored) {
                // skip invalid entry
            }
        }
        return List.copyOf(items);
    }

    private static JsonObject defaultActivityObject() {
        JsonObject fallback = new JsonObject();
        fallback.add("items", new JsonArray());
        fallback.addProperty("count", 0);
        return fallback;
    }
}

