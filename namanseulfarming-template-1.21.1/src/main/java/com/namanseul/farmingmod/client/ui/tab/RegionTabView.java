package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class RegionTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Region");
    }

    @Override
    public Component openButtonLabel() {
        return Component.translatable("screen.namanseulfarming.status.open_button");
    }

    @Override
    public Component actionTitle() {
        return Component.literal("Open Region Status");
    }

    @Override
    public Component actionHint() {
        return Component.literal("Check focus area, events, and overall region momentum.");
    }

    @Override
    public List<Component> summaryLines(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Loading region snapshot..."));
        }

        return List.of(
                Component.literal("Current focus area: " + summary.currentFocusRegion()),
                Component.literal("Region progress: " + summary.regionProgressPercent() + "%")
        );
    }
}
