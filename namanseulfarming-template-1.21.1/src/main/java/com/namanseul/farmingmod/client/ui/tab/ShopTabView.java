package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Shop");
    }

    @Override
    public Component openButtonLabel() {
        return Component.translatable("screen.namanseulfarming.shop.open_button");
    }

    @Override
    public Component actionTitle() {
        return Component.literal("Open Market");
    }

    @Override
    public Component actionHint() {
        return Component.literal("Buy what you need and sell what you carry.");
    }

    @Override
    public List<Component> summaryLines(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Loading market snapshot..."));
        }

        return List.of(
                Component.literal("Best for quick trading decisions."),
                Component.literal("Current reference price: " + summary.shopPricePreview())
        );
    }
}
