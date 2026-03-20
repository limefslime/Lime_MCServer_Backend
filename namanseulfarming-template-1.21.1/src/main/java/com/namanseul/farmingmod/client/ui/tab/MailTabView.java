package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Mail");
    }

    @Override
    public List<Component> buildListEntries(@Nullable HubSummaryData summary) {
        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("Mail UI ready for stage 3"));
        entries.add(Component.literal("Use Open Mail button to enter"));
        entries.add(Component.literal("List / detail / claim flow enabled"));
        if (summary != null) {
            entries.add(Component.literal("Unclaimed mail preview: " + summary.unclaimedMailCount()));
        }
        return entries;
    }

    @Override
    public List<Component> buildDetailLines(@Nullable HubSummaryData summary, int selectedIndex) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("[Mail]"));
        lines.add(Component.literal("Open Mail screen for mailbox list/detail/claim actions."));
        if (summary != null) {
            lines.add(Component.literal("Server summary unclaimed: " + summary.unclaimedMailCount()));
        }
        return lines;
    }
}
