package com.namanseul.farmingmod.client.ui.status;

import java.text.NumberFormat;
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
            return List.of(Component.literal("Loading world status..."));
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
            return List.of(Component.literal("Status details will appear after loading."));
        }

        return switch (tabId) {
            case TAB_FOCUS -> buildFocusDetail(data);
            case TAB_REGION -> buildRegionDetail(data, selectedIndex);
            case TAB_EVENT -> buildEventDetail(data, selectedIndex);
            case TAB_COMPLETION -> buildCompletionDetail(data, selectedIndex);
            default -> List.of();
        };
    }

    public static List<Component> buildSummaryLines(@Nullable StatusOverviewData data) {
        if (data == null) {
            return List.of(Component.literal("Loading world status..."));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Focus region: " + data.focus().region()));
        lines.add(Component.literal("Tracked regions: " + data.regionCount()));
        lines.add(Component.literal("Live events: " + data.activeEventCount()));
        lines.add(Component.literal("Active effects: " + data.activeProjectEffectCount()));
        if (data.rewardReadyMailCount() > 0) {
            lines.add(Component.literal("Rewards ready: " + data.rewardReadyMailCount() + " mails"));
        } else {
            lines.add(Component.literal("Completed projects: " + data.completedProjectCount()));
        }
        if (data.partial()) {
            lines.add(Component.literal("Some details are still updating."));
        }
        return lines;
    }

    private static List<Component> buildFocusEntries(StatusOverviewData data) {
        if (!data.focus().available()) {
            return List.of(Component.literal("No focus region selected right now."));
        }

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Current focus: " + data.focus().region()));
        if (!data.focus().status().isBlank()) {
            entries.add(Component.literal("Status: " + data.focus().status()));
        }
        return entries;
    }

    private static List<Component> buildFocusDetail(StatusOverviewData data) {
        if (!data.focus().available()) {
            return List.of(Component.literal("Focus details are not available."));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Top activity region: " + data.focus().region()));
        if (!data.focus().status().isBlank()) {
            lines.add(Component.literal("Current status: " + data.focus().status()));
        }
        if (!data.focus().sourceCategory().isBlank()) {
            lines.add(Component.literal("Main driver: " + data.focus().sourceCategory()));
        }
        return lines;
    }

    private static List<Component> buildRegionEntries(StatusOverviewData data) {
        if (data.regions().isEmpty()) {
            return List.of(Component.literal("No regional progress data."));
        }

        List<Component> entries = new ArrayList<>();
        for (StatusOverviewData.RegionSnapshot region : data.regions()) {
            entries.add(Component.literal(region.region()
                    + " | "
                    + region.progressPercent()
                    + "%"
                    + " | Lv "
                    + region.level()));
        }
        return entries;
    }

    private static List<Component> buildRegionDetail(StatusOverviewData data, int selectedIndex) {
        if (data.regions().isEmpty()) {
            return List.of(Component.literal("No region details available."));
        }

        StatusOverviewData.RegionSnapshot selected = data.regions().get(clampIndex(selectedIndex, data.regions().size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Region: " + selected.region()));
        lines.add(Component.literal("Progress: " + selected.progressPercent() + "%"));
        lines.add(Component.literal("Level: " + selected.level()));
        if (!selected.dominantCategory().isBlank()) {
            lines.add(Component.literal("Dominant category: " + selected.dominantCategory()));
        }
        if (selected.currentSellTotal() > 0) {
            lines.add(Component.literal("Recent sales volume: " + formatNumber(selected.currentSellTotal())));
        }
        return lines;
    }

    private static List<Component> buildEventEntries(StatusOverviewData data) {
        if (data.activeEvents().isEmpty()) {
            return List.of(Component.literal("No active events right now."));
        }

        List<Component> entries = new ArrayList<>();
        for (StatusOverviewData.EventSnapshot event : data.activeEvents()) {
            String state = event.state().isBlank() ? "Live" : event.state();
            entries.add(Component.literal(event.title() + " | " + state));
        }
        return entries;
    }

    private static List<Component> buildEventDetail(StatusOverviewData data, int selectedIndex) {
        if (data.activeEvents().isEmpty()) {
            return List.of(Component.literal("No event details available."));
        }

        StatusOverviewData.EventSnapshot selected = data.activeEvents().get(clampIndex(selectedIndex, data.activeEvents().size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Event: " + selected.title()));
        lines.add(Component.literal("Region: " + selected.region()));
        if (!selected.effectLabel().isBlank()) {
            lines.add(Component.literal("Effect: " + selected.effectLabel()));
        }
        if (!selected.state().isBlank()) {
            lines.add(Component.literal("State: " + selected.state()));
        }
        return lines;
    }

    private static List<Component> buildCompletionEntries(StatusOverviewData data) {
        if (!data.completedProjects().isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (StatusOverviewData.CompletionSnapshot row : data.completedProjects()) {
                entries.add(Component.literal(row.projectId() + " | " + completionState(row)));
            }
            return entries;
        }

        if (!data.projectEffects().isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (StatusOverviewData.EffectSnapshot effect : data.projectEffects()) {
                entries.add(Component.literal(effect.projectId() + " | " + (effect.active() ? "Effect Active" : "Effect Paused")));
            }
            return entries;
        }

        return List.of(Component.literal("No project completion records yet."));
    }

    private static List<Component> buildCompletionDetail(StatusOverviewData data, int selectedIndex) {
        if (!data.completedProjects().isEmpty()) {
            StatusOverviewData.CompletionSnapshot row = data.completedProjects().get(clampIndex(selectedIndex, data.completedProjects().size()));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Project: " + row.projectId()));
            lines.add(Component.literal("Status: " + completionState(row)));
            if (row.rewardMailCount() > 0) {
                lines.add(Component.literal("Reward mails: " + row.rewardMailCount()));
                lines.add(Component.literal("Reward amount: " + formatNumber(row.rewardTotalAmount())));
            }
            if (row.effectActive()) {
                lines.add(Component.literal("Project effect is currently active."));
            }
            return lines;
        }

        if (!data.projectEffects().isEmpty()) {
            StatusOverviewData.EffectSnapshot effect = data.projectEffects().get(clampIndex(selectedIndex, data.projectEffects().size()));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("Project: " + effect.projectId()));
            lines.add(Component.literal("Effect: " + buildEffectText(effect)));
            lines.add(Component.literal("State: " + (effect.active() ? "Active" : "Paused")));
            return lines;
        }

        return List.of(Component.literal("No completion details available."));
    }

    private static String buildEffectText(StatusOverviewData.EffectSnapshot effect) {
        StringBuilder text = new StringBuilder();
        if (!effect.target().isBlank()) {
            text.append(effect.target()).append(" ");
        }
        if (!effect.effectType().isBlank()) {
            text.append(effect.effectType()).append(" ");
        }
        text.append(formatEffectValue(effect.effectValue()));
        return text.toString().trim();
    }

    private static String formatEffectValue(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format("%.2f", value);
    }

    private static String formatNumber(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static String completionState(StatusOverviewData.CompletionSnapshot row) {
        if (row.completed() && row.rewardMailCount() > 0) {
            return "Reward Ready";
        }
        if (row.completed()) {
            return "Completed";
        }
        return "In Progress";
    }

    private static int clampIndex(int selectedIndex, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(selectedIndex, size - 1));
    }
}
