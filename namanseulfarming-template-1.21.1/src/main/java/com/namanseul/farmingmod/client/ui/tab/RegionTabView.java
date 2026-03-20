package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class RegionTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Region");
    }

    @Override
    public List<Component> buildListEntries(@Nullable HubSummaryData summary) {
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Status UI ready for stage 5"));
        entries.add(Component.literal("Use Open Status button to enter"));
        entries.add(Component.literal("Region / Focus / Event / Completion data"));
        if (summary != null) {
            entries.add(Component.literal("current focus: " + summary.currentFocusRegion()));
            entries.add(Component.literal("dominant category: " + summary.dominantRegionCategory()));
        }
        return entries;
    }

    @Override
    public List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[Status]"));
        lines.add(Component.literal("Open Status screen for region/focus/event/completion details."));
        if (summary != null) {
            lines.add(Component.literal("server summary region progress: " + summary.regionProgressPercent() + "%"));
            lines.add(Component.literal("active event count: " + summary.activeEventCount()));
            lines.add(Component.literal("active project effects: " + summary.activeProjectEffectCount()));
        }
        return lines;
    }
}