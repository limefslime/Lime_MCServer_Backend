package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.StatusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RegionTabView implements HubTabView {
    @Override
    public Component menuLabel() {
        return Component.literal("Region");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new StatusScreen(hubScreen));
    }
}
