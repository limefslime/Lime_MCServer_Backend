package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.ShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ShopTabView implements HubTabView {
    @Override
    public Component menuLabel() {
        return Component.literal("Shop");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new ShopScreen(hubScreen));
    }
}
