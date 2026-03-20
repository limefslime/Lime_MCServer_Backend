package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Invest");
    }

    @Override
    public List<Component> buildListEntries(@Nullable HubSummaryData summary) {
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Invest UI ready for stage 4"));
        entries.add(Component.literal("Use Open Invest button to enter"));
        entries.add(Component.literal("Project list / detail / contribute flow enabled"));
        if (summary != null) {
            entries.add(Component.literal("Hub invest progress preview: " + summary.investProgressPercent() + "%"));
        }
        return entries;
    }

    @Override
    public List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[Invest]"));
        lines.add(Component.literal("Open Invest screen for list/detail/progress/contribute actions."));
        if (summary != null) {
            lines.add(Component.literal("Server summary invest progress: " + summary.investProgressPercent() + "%"));
        }
        return lines;
    }
}
