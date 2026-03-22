package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Legacy compatibility redirect; player UI is not rendered anymore.
 */
public final class PlayerOverviewScreen extends BaseGameScreen {
    @Nullable
    private final Screen returnScreen;
    private boolean redirected;

    public PlayerOverviewScreen(@Nullable Screen returnScreen) {
        super(Component.empty());
        this.returnScreen = returnScreen;
    }

    public static PlayerOverviewScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        PlayerOverviewScreen screen = new PlayerOverviewScreen(parent);
        minecraft.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        redirectToHub();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        redirectToHub();
    }

    @Override
    public void onClose() {
        redirectToHub();
    }

    public void handleServerResponse(UiResponsePayload payload) {
        redirectToHub();
    }

    private void redirectToHub() {
        if (redirected) {
            return;
        }
        redirected = true;
        Minecraft minecraft = Minecraft.getInstance();
        Screen fallback = returnScreen == null ? new GameHubScreen() : returnScreen;
        minecraft.setScreen(fallback);
    }
}
