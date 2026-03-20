package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.StatusScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class RegionTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Region");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new StatusScreen(hubScreen));
    }

    @Override
    public List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Region signals are loading."));
        }

        String focus = summary.currentFocusRegion();
        int progress = Math.max(0, Math.min(100, summary.regionProgressPercent()));
        if (focus != null && !focus.isBlank()) {
            return List.of(
                    Component.literal("Current focus region: " + focus),
                    Component.literal("Overall region progress: " + progress + "%")
            );
        }
        return List.of(
                Component.literal("No dominant focus region right now."),
                Component.literal("Open Region to check event and progress details.")
        );
    }
}
