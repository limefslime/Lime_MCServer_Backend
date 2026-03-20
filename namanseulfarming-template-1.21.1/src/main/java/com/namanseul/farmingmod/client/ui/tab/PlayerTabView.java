package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PlayerTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("My");
    }

    @Override
    public Component openButtonLabel() {
        return Component.translatable("screen.namanseulfarming.player.open_button");
    }

    @Override
    public Component actionTitle() {
        return Component.literal("Open My Overview");
    }

    @Override
    public Component actionHint() {
        return Component.literal("Review wallet state and recent personal progress.");
    }

    @Override
    public List<Component> summaryLines(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Loading personal snapshot..."));
        }

        return List.of(
                Component.literal("Unclaimed rewards: " + summary.unclaimedMailCount()),
                Component.literal("Investment progress: " + summary.investProgressPercent() + "%")
        );
    }
}
