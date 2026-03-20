package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.ShopScreen;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopTabView implements HubTabView {
    @Override
    public Component tabLabel() {
        return Component.literal("Shop");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new ShopScreen(hubScreen));
    }

    @Override
    public List<Component> buildEntryHints(@Nullable HubSummaryData summary) {
        if (summary == null) {
            return List.of(Component.literal("Market signals are loading."));
        }

        int previewPrice = summary.shopPricePreview();
        if (previewPrice > 0) {
            return List.of(
                    Component.literal("Open Market to buy or sell immediately."),
                    Component.literal("Reference price: " + previewPrice)
            );
        }
        return List.of(Component.literal("Open Market for current item prices and stock."));
    }
}
