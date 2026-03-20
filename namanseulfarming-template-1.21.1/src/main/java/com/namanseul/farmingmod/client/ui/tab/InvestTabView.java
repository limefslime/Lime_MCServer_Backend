package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Invest");
    }

    @Override
    public Component openButtonLabel() {
        return Component.translatable("screen.namanseulfarming.invest.open_button");
    }

    @Override
    public Component actionTitle() {
        return Component.literal("Open Investment");
    }

    @Override
    public Component actionHint() {
        return Component.literal("Check project progress and contribute resources.");
    }

    @Override
    public List<Component> summaryLines(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Loading investment snapshot..."));
        }

        return List.of(
                Component.literal("Current progress: " + summary.investProgressPercent() + "%"),
                Component.literal("Open this tab to push progress further.")
        );
    }
}
