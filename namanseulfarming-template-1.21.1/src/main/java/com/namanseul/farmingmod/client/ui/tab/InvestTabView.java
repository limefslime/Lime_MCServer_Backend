package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.InvestScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Invest");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new InvestScreen(hubScreen));
    }

    @Override
    public List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Project signals are loading."));
        }

        int progress = Math.max(0, Math.min(100, summary.investProgressPercent()));
        if (progress >= 100) {
            return List.of(
                    Component.literal("Current tracked project is completed."),
                    Component.literal("Open Investment to pick next contribution target.")
            );
        }
        return List.of(
                Component.literal("Current project progress: " + progress + "%"),
                Component.literal("Open Investment to contribute and push progress.")
        );
    }
}
