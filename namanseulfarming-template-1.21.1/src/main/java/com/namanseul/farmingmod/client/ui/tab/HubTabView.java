package com.namanseul.farmingmod.client.ui.tab;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import net.minecraft.network.chat.Component;

public interface HubTabView {
    Component menuLabel();

    void openFromHub(GameHubScreen hubScreen);
}
