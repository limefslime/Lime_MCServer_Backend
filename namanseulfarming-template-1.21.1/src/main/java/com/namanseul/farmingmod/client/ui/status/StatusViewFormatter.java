package com.namanseul.farmingmod.client.ui.status;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class StatusViewFormatter {
    public static final String TAB_FOCUS = "focus";
    public static final String TAB_REGION = "region";
    public static final String TAB_EVENT = "event";
    public static final String TAB_COMPLETION = "completion";

    private StatusViewFormatter() {}

    public static List<Component> buildListEntries(@Nullable StatusOverviewData data, String tabId) {
        if (data == null) {
            return List.of(Component.literal("waiting for server response..."));
        }

        return switch (tabId) {
            case TAB_FOCUS -> buildFocusEntries(data);
            case TAB_REGION -> buildRegionEntries(data);
            case TAB_EVENT -> buildEventEntries(data);
            case TAB_COMPLETION -> buildCompletionEntries(data);
            default -> List.of();
        };
    }

    public static List<Component> buildDetailLines(@Nullable StatusOverviewData data, String tabId, int selectedIndex) {
        if (data == null) {
            return List.of(Component.literal("no status data yet"));
        }

        return switch (tabId) {
            case TAB_FOCUS -> buildFocusDetail(data);
            case TAB_REGION -> buildRegionDetail(data, selectedIndex);
            case TAB_EVENT -> buildEventDetail(data, selectedIndex);
            case TAB_COMPLETION -> buildCompletionDetail(data, selectedIndex);
            default -> List.of();
        };
    }

    private static List<Component> buildFocusEntries(StatusOverviewData data) {
        JsonObject focus = data.focus();
        String region = readString(focus, "focusRegion", "-");
        String status = readString(focus, "status", "-");
        return List.of(Component.literal("Focus: " + region + " (" + status + ")"));
    }

    private static List<Component> buildFocusDetail(StatusOverviewData data) {
        JsonObject focus = data.focus();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("focusRegion: " + readString(focus, "focusRegion", "-")));
        lines.add(Component.literal("status: " + readString(focus, "status", "-")));
        lines.add(Component.literal("sourceCategory: " + readString(focus, "sourceCategory", "-")));
        lines.add(Component.literal("calculatedAt: " + readString(focus, "calculatedAt", "-")));
        lines.add(Component.literal("resolvedFromCurrentCategories: " + readBoolean(focus, "resolvedFromCurrentCategories")));

        JsonObject totals = readObject(focus, "recentSellTotals");
        lines.add(Component.literal("recentSellTotals.farming: " + readNumberText(totals, "farming", "0")));
        lines.add(Component.literal("recentSellTotals.fishing: " + readNumberText(totals, "fishing", "0")));
        lines.add(Component.literal("recentSellTotals.mining: " + readNumberText(totals, "mining", "0")));
        return lines;
    }

    private static List<Component> buildRegionEntries(StatusOverviewData data) {
        List<JsonObject> regions = data.regions();
        if (regions.isEmpty()) {
            return List.of(Component.literal("no regions returned"));
        }

        List<Component> entries = new ArrayList<>();
        for (JsonObject region : regions) {
            String name = readString(region, "region", "-");
            String level = readNumberText(region, "level", "0");
            int percent = toPercent(readDouble(region, "progressPercent"));
            entries.add(Component.literal(name + " | lvl " + level + " | " + percent + "%"));
        }
        return entries;
    }

    private static List<Component> buildRegionDetail(StatusOverviewData data, int selectedIndex) {
        List<JsonObject> regions = data.regions();
        if (regions.isEmpty()) {
            return List.of(Component.literal("no region detail available"));
        }

        JsonObject selected = regions.get(clampIndex(selectedIndex, regions.size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("region: " + readString(selected, "region", "-")));
        lines.add(Component.literal("level: " + readNumberText(selected, "level", "0")));
        lines.add(Component.literal("xp: " + readNumberText(selected, "xp", "0")));
        lines.add(Component.literal("progressPercent: " + toPercent(readDouble(selected, "progressPercent")) + "%"));
        lines.add(Component.literal("currentSellTotal: " + readNumberText(selected, "currentSellTotal", "0")));
        lines.add(Component.literal("dominantCategory: " + readString(selected, "dominantCategory", "-")));
        lines.add(Component.literal("mappedLegacyRegionKey: " + readString(selected, "mappedLegacyRegionKey", "-")));

        JsonObject operations = readObject(selected, "operations");
        lines.add(Component.literal("operations.currentFocusRegion: " + readString(operations, "currentFocusRegion", "-")));
        lines.add(Component.literal("operations.activeEventCount: " + readNumberText(operations, "activeEventCount", "0")));
        lines.add(Component.literal("operations.activeProjectEffectCount: "
                + readNumberText(operations, "activeProjectEffectCount", "0")));
        return lines;
    }

    private static List<Component> buildEventEntries(StatusOverviewData data) {
        List<JsonObject> events = data.activeEvents();
        if (events.isEmpty()) {
            return List.of(Component.literal("no active events"));
        }

        List<Component> entries = new ArrayList<>();
        for (JsonObject event : events) {
            String id = readString(event, "id", "-");
            String region = readString(event, "region", "-");
            String effectType = readString(event, "effect_type", readString(event, "effectType", "-"));
            entries.add(Component.literal(id + " | " + region + " | " + effectType));
        }
        return entries;
    }

    private static List<Component> buildEventDetail(StatusOverviewData data, int selectedIndex) {
        List<JsonObject> events = data.activeEvents();
        if (events.isEmpty()) {
            return List.of(Component.literal("no event detail available"));
        }

        JsonObject selected = events.get(clampIndex(selectedIndex, events.size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("id: " + readString(selected, "id", "-")));
        lines.add(Component.literal("name: " + readString(selected, "name", "-")));
        lines.add(Component.literal("region: " + readString(selected, "region", "-")));
        lines.add(Component.literal("effectType: " + readString(selected, "effect_type",
                readString(selected, "effectType", "-"))));
        lines.add(Component.literal("effectValue: " + readNumberText(selected, "effect_value",
                readNumberText(selected, "effectValue", "0"))));
        lines.add(Component.literal("status: " + readString(selected, "status", "-")));
        lines.add(Component.literal("runtimeStatus: " + readString(selected, "runtimeStatus", "-")));
        lines.add(Component.literal("isRuntimeActive: " + readBoolean(selected, "isRuntimeActive")));
        lines.add(Component.literal("startsAt: " + readString(selected, "startsAt", "-")));
        lines.add(Component.literal("endsAt: " + readString(selected, "endsAt", "-")));
        return lines;
    }

    private static List<Component> buildCompletionEntries(StatusOverviewData data) {
        List<JsonObject> completed = data.completedProjects();
        if (!completed.isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (JsonObject row : completed) {
                entries.add(Component.literal(readString(row, "projectId", "-")
                        + " | completed=" + readBoolean(row, "isCompleted")
                        + " | rewards=" + readNumberText(row, "rewardTotalAmount", "0")));
            }
            return entries;
        }

        List<JsonObject> effects = data.projectEffects();
        if (effects.isEmpty()) {
            return List.of(Component.literal("no completion/effect records"));
        }

        List<Component> entries = new ArrayList<>();
        for (JsonObject effect : effects) {
            entries.add(Component.literal(readString(effect, "project_id", "-")
                    + " | " + readString(effect, "effect_target", "-")
                    + " | " + readNumberText(effect, "effect_value", "0")));
        }
        return entries;
    }

    private static List<Component> buildCompletionDetail(StatusOverviewData data, int selectedIndex) {
        List<JsonObject> completed = data.completedProjects();
        if (!completed.isEmpty()) {
            JsonObject row = completed.get(clampIndex(selectedIndex, completed.size()));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("projectId: " + readString(row, "projectId", "-")));
            lines.add(Component.literal("isCompleted: " + readBoolean(row, "isCompleted")));
            lines.add(Component.literal("isEffectActive: " + readBoolean(row, "isEffectActive")));
            lines.add(Component.literal("effectTarget: " + readString(row, "effectTarget", "-")));
            lines.add(Component.literal("effectType: " + readString(row, "effectType", "-")));
            lines.add(Component.literal("completionProcessed: " + readBoolean(row, "completionProcessed")));
            lines.add(Component.literal("completionMailSent: " + readBoolean(row, "completionMailSent")));
            lines.add(Component.literal("rewardMailCount: " + readNumberText(row, "rewardMailCount", "0")));
            lines.add(Component.literal("rewardTotalAmount: " + readNumberText(row, "rewardTotalAmount", "0")));
            return lines;
        }

        List<JsonObject> effects = data.projectEffects();
        if (effects.isEmpty()) {
            return List.of(Component.literal("no completion/effect detail available"));
        }

        JsonObject row = effects.get(clampIndex(selectedIndex, effects.size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("id: " + readString(row, "id", "-")));
        lines.add(Component.literal("project_id: " + readString(row, "project_id", "-")));
        lines.add(Component.literal("effect_type: " + readString(row, "effect_type", "-")));
        lines.add(Component.literal("effect_target: " + readString(row, "effect_target", "-")));
        lines.add(Component.literal("effect_value: " + readNumberText(row, "effect_value", "0")));
        lines.add(Component.literal("is_active: " + readBoolean(row, "is_active")));
        return lines;
    }

    private static int clampIndex(int selectedIndex, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(selectedIndex, size - 1));
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
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key) {
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

    private static String readNumberText(JsonObject root, String key, String fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.getAsJsonPrimitive().isNumber()) {
                double value = element.getAsDouble();
                if (Math.rint(value) == value) {
                    return Integer.toString((int) value);
                }
                return String.format("%.2f", value);
            }
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Double readDouble(JsonObject root, String key) {
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

    private static int toPercent(@Nullable Double value) {
        if (value == null) {
            return 0;
        }
        double normalized = value <= 1.0 ? value * 100.0 : value;
        return Math.max(0, Math.min(100, (int) Math.round(normalized)));
    }
}

