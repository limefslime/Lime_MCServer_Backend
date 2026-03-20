package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.PlayerOverviewScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PlayerTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("My");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new PlayerOverviewScreen(hubScreen));
    }

    @Override
    public List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Personal overview signals are loading."));
        }

        int rewardCount = summary.unclaimedMailCount();
        int investProgress = Math.max(0, Math.min(100, summary.investProgressPercent()));
        if (rewardCount > 0) {
            return List.of(
                    Component.literal("Rewards are waiting for your account."),
                    Component.literal("Open My tab to check wallet and activity.")
            );
        }
        return List.of(
                Component.literal("No pending rewards right now."),
                Component.literal("Recent project progress: " + investProgress + "%")
        );
    }
}
