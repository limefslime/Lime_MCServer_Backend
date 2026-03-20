package com.namanseul.farmingmod.client.input;

import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class ClientKeyInputHandler {
    private ClientKeyInputHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen != null) {
            return;
        }

        while (ModKeyMappings.OPEN_HUB.consumeClick()) {
            GameHubScreen.openFromKeybind();
        }
    }
}
