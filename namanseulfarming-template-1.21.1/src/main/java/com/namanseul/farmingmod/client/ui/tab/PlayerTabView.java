package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PlayerTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("My");
    }

    @Override
    public List<Component> buildListEntries(@Nullable HubSummaryData summary) {
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Player overview UI ready for stage 6"));
        entries.add(Component.literal("Use Open Overview button to enter"));
        entries.add(Component.literal("Wallet / Recent Activity / Summary"));
        if (summary != null) {
            entries.add(Component.literal("unclaimed mails: " + summary.unclaimedMailCount()));
            entries.add(Component.literal("invest progress: " + summary.investProgressPercent() + "%"));
        }
        return entries;
    }

    @Override
    public List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[Player Overview]"));
        lines.add(Component.literal("Open unified player assets and state panel."));
        if (summary != null) {
            lines.add(Component.literal("summary focus: " + summary.currentFocusRegion()));
            lines.add(Component.literal("summary active events: " + summary.activeEventCount()));
            lines.add(Component.literal("summary active effects: " + summary.activeProjectEffectCount()));
        }
        return lines;
    }
}

