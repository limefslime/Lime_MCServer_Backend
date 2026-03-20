package com.namanseul.farmingmod.client.ui.status;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StatusJsonParser {
    private StatusJsonParser() {}

    public static StatusOverviewData parseOverview(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("status overview payload is empty");
        }

        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("status overview payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject focus = readObject(root, "focus");
        List<JsonObject> regions = readObjectArray(root, "regions");
        List<JsonObject> activeEvents = readObjectArray(root, "activeEvents");
        List<JsonObject> projectEffects = readObjectArray(root, "projectEffects");
        List<JsonObject> completedProjects = readObjectArray(root, "completedProjects");
        boolean partial = readBoolean(root, "partial");
        List<String> partialNotes = readStringArray(root, "partialNotes");
        String generatedAt = readString(root, "generatedAt");

        return new StatusOverviewData(
                focus == null ? new JsonObject() : focus,
                regions,
                activeEvents,
                projectEffects,
                completedProjects,
                partial,
                partialNotes,
                generatedAt
        );
    }

    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static List<JsonObject> readObjectArray(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            return List.of();
        }

        List<JsonObject> objects = new ArrayList<>();
        for (JsonElement entry : array) {
            if (entry != null && entry.isJsonObject()) {
                objects.add(entry.getAsJsonObject());
            }
        }
        return Collections.unmodifiableList(objects);
    }

    private static List<String> readStringArray(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        List<String> items = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || entry.isJsonNull()) {
                continue;
            }
            try {
                String value = entry.getAsString();
                if (value != null && !value.isBlank()) {
                    items.add(value);
                }
            } catch (Exception ignored) {
                // skip invalid note entries
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static boolean readBoolean(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }
}

