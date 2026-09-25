package com.namanseul.farmingmod.client.ui.status;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public final class StatusJsonParser {
    private StatusJsonParser() {}

    public static StatusOverviewData parseOverview(String json) {
        JsonObject root = parseRootObject(json);

        StatusOverviewData.FocusSnapshot focus = parseFocus(readObject(root, "focus"));
        List<StatusOverviewData.RegionSnapshot> regions = parseRegions(readObjectArray(root, "regions"));
        List<StatusOverviewData.EventSnapshot> activeEvents = parseEvents(readObjectArray(root, "activeEvents"));
        List<StatusOverviewData.EffectSnapshot> projectEffects = parseEffects(readObjectArray(root, "projectEffects"));
        List<StatusOverviewData.CompletionSnapshot> completedProjects = parseCompletions(readObjectArray(root, "completedProjects"));
        boolean partial = readBoolean(root, "partial");

        return new StatusOverviewData(
                focus,
                regions,
                activeEvents,
                projectEffects,
                completedProjects,
                partial
        );
    }

    private static StatusOverviewData.FocusSnapshot parseFocus(@Nullable JsonObject focus) {
        if (focus == null || focus.entrySet().isEmpty()) {
            return StatusOverviewData.FocusSnapshot.empty();
        }

        String region = readString(focus, "focusRegion", "-");
        String status = normalizeFocusStatus(readString(focus, "status", ""));
        String sourceCategory = humanizeWords(readString(focus, "sourceCategory", ""));
        boolean available = !region.equals("-") || !status.isBlank() || !sourceCategory.isBlank();

        return new StatusOverviewData.FocusSnapshot(region, status, sourceCategory, available);
    }

    private static List<StatusOverviewData.RegionSnapshot> parseRegions(List<JsonObject> rawRegions) {
        if (rawRegions.isEmpty()) {
            return List.of();
        }

        List<StatusOverviewData.RegionSnapshot> parsed = new ArrayList<>();
        for (JsonObject region : rawRegions) {
            parsed.add(new StatusOverviewData.RegionSnapshot(
                    readString(region, "region", "-"),
                    readInt(region, "level", 0),
                    toPercent(readDouble(region, "progressPercent")),
                    readInt(region, "currentSellTotal", 0),
                    readString(region, "dominantCategory", "")
            ));
        }
        return List.copyOf(parsed);
    }

    private static List<StatusOverviewData.EventSnapshot> parseEvents(List<JsonObject> rawEvents) {
        if (rawEvents.isEmpty()) {
            return List.of();
        }

        List<StatusOverviewData.EventSnapshot> parsed = new ArrayList<>();
        for (JsonObject event : rawEvents) {
            String effectType = readFirstString(event, "effect_type", "effectType");
            Double effectValue = readFirstDouble(event, "effect_value", "effectValue");
            parsed.add(new StatusOverviewData.EventSnapshot(
                    readFirstString(event, "name", "id"),
                    readString(event, "region", "-"),
                    buildEffectLabel(effectType, effectValue),
                    normalizeEventStatus(readFirstString(event, "runtimeStatus", "status"), readBoolean(event, "isRuntimeActive")),
                    readBoolean(event, "isRuntimeActive")
            ));
        }
        return List.copyOf(parsed);
    }

    private static List<StatusOverviewData.EffectSnapshot> parseEffects(List<JsonObject> rawEffects) {
        if (rawEffects.isEmpty()) {
            return List.of();
        }

        List<StatusOverviewData.EffectSnapshot> parsed = new ArrayList<>();
        for (JsonObject effect : rawEffects) {
            Double effectValue = readFirstDouble(effect, "effectValue", "effect_value");
            parsed.add(new StatusOverviewData.EffectSnapshot(
                    readFirstString(effect, "projectId", "project_id"),
                    humanizeWords(readFirstString(effect, "effectTarget", "effect_target")),
                    humanizeWords(readFirstString(effect, "effectType", "effect_type")),
                    effectValue == null ? 0.0 : effectValue,
                    readFirstBoolean(effect, "isEffectActive", "is_active")
            ));
        }
        return List.copyOf(parsed);
    }

    private static List<StatusOverviewData.CompletionSnapshot> parseCompletions(List<JsonObject> rawCompleted) {
        if (rawCompleted.isEmpty()) {
            return List.of();
        }

        List<StatusOverviewData.CompletionSnapshot> parsed = new ArrayList<>();
        for (JsonObject completion : rawCompleted) {
            parsed.add(new StatusOverviewData.CompletionSnapshot(
                    readString(completion, "projectId", "-"),
                    readBoolean(completion, "isCompleted"),
                    readBoolean(completion, "isEffectActive"),
                    readInt(completion, "rewardMailCount", 0),
                    readInt(completion, "rewardTotalAmount", 0)
            ));
        }
        return List.copyOf(parsed);
    }

    private static JsonObject parseRootObject(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("status overview payload is empty");
        }

        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("status overview payload is not an object");
        }
        return parsed.getAsJsonObject();
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

    private static List<JsonObject> readObjectArray(@Nullable JsonObject root, String key) {
        if (root == null) {
            return List.of();
        }

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
        return List.copyOf(objects);
    }

    private static boolean readBoolean(@Nullable JsonObject root, String key) {
        if (root == null) {
            return false;
        }

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

    private static boolean readFirstBoolean(@Nullable JsonObject root, String... keys) {
        if (root == null) {
            return false;
        }

        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                return element.getAsBoolean();
            } catch (Exception ignored) {
                // keep searching
            }
        }
        return false;
    }

    private static String readString(@Nullable JsonObject root, String key, String fallback) {
        if (root == null) {
            return fallback;
        }

        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readFirstString(@Nullable JsonObject root, String... keys) {
        if (root == null) {
            return "";
        }

        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            } catch (Exception ignored) {
                // keep searching
            }
        }
        return "";
    }

    private static int readInt(@Nullable JsonObject root, String key, int fallback) {
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

    @Nullable
    private static Double readDouble(@Nullable JsonObject root, String key) {
        if (root == null) {
            return null;
        }

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

    @Nullable
    private static Double readFirstDouble(@Nullable JsonObject root, String... keys) {
        if (root == null) {
            return null;
        }

        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                return element.getAsDouble();
            } catch (Exception ignored) {
                // keep searching
            }
        }
        return null;
    }

    private static int toPercent(@Nullable Double value) {
        if (value == null) {
            return 0;
        }

        double normalized = value <= 1.0 ? value * 100.0 : value;
        return Math.max(0, Math.min(100, (int) Math.round(normalized)));
    }

    private static String buildEffectLabel(String effectType, @Nullable Double effectValue) {
        String type = effectType == null || effectType.isBlank() ? "Effect" : humanizeWords(effectType);
        if (effectValue == null) {
            return type;
        }

        double value = effectValue;
        if (Math.rint(value) == value) {
            return type + " " + (int) value;
        }
        return type + " " + String.format("%.2f", value);
    }

    private static String normalizeFocusStatus(String rawStatus) {
        String normalized = normalizeKey(rawStatus);
        return switch (normalized) {
            case "active", "running" -> "Active";
            case "idle", "waiting", "cooldown" -> "Waiting";
            case "completed", "complete", "done", "ended" -> "Completed";
            default -> humanizeWords(rawStatus);
        };
    }

    private static String normalizeEventStatus(String rawStatus, boolean runtimeActive) {
        String normalized = normalizeKey(rawStatus);
        if ("active".equals(normalized) || "running".equals(normalized)) {
            return "Live";
        }
        if ("waiting".equals(normalized) || "pending".equals(normalized) || "ready".equals(normalized)) {
            return "Starting Soon";
        }
        if ("completed".equals(normalized) || "ended".equals(normalized) || "closed".equals(normalized)) {
            return "Ended";
        }
        if (normalized.isBlank()) {
            return runtimeActive ? "Live" : "Paused";
        }
        return humanizeWords(rawStatus);
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String humanizeWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.trim().replace('_', ' ').replace('-', ' ');
        if (normalized.isBlank()) {
            return "";
        }

        String[] words = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String lower = word.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.toString();
    }
}
