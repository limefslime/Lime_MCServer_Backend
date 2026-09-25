package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.InvestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class InvestTabView implements HubTabView {
    @Override
    public Component menuLabel() {
        return Component.literal("Invest");
    }

    @Override
    public void openFromHub(GameHubScreen hubScreen) {
        Minecraft.getInstance().setScreen(new InvestScreen(hubScreen));
    }
}
