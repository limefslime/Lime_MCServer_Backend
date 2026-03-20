package com.namanseul.farmingmod.client.network;

import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.client.ui.screen.GameHubScreen;
import com.namanseul.farmingmod.client.ui.screen.InvestScreen;
import com.namanseul.farmingmod.client.ui.screen.MailScreen;
import com.namanseul.farmingmod.client.ui.screen.PlayerOverviewScreen;
import com.namanseul.farmingmod.client.ui.screen.ShopScreen;
import com.namanseul.farmingmod.client.ui.screen.StatusScreen;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientUiResponseDispatcher {
    private ClientUiResponseDispatcher() {}

    public static void handle(UiResponsePayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.info(
                        "[UI] recv response S->C id={} success={} action={} screen={}",
                        payload.requestId(),
                        payload.success(),
                        payload.action().serialized(),
                        payload.screenType().serialized()
                );
            }

            switch (payload.screenType()) {
                case HUB -> handleHub(payload, minecraft);
                case SHOP -> handleShop(payload, minecraft);
                case MAIL -> handleMail(payload, minecraft);
                case INVEST -> handleInvest(payload, minecraft);
                case STATUS -> handleStatus(payload, minecraft);
                case PLAYER -> handlePlayer(payload, minecraft);
            }
        });
    }

    private static void handleHub(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            GameHubScreen.openFromCommand();
            return;
        }

        if (minecraft.screen instanceof GameHubScreen hubScreen) {
            hubScreen.handleServerResponse(payload);
            return;
        }

        if (payload.success() && payload.data() != null) {
            GameHubScreen autoOpened = new GameHubScreen();
            minecraft.setScreen(autoOpened);
            autoOpened.handleServerResponse(payload);
        }
    }

    private static void handleShop(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            ShopScreen.openStandalone();
            return;
        }

        if (minecraft.screen instanceof ShopScreen shopScreen) {
            shopScreen.handleServerResponse(payload);
            return;
        }

        if (shouldAutoOpenJsonScreen(payload)) {
            ShopScreen autoOpened = ShopScreen.openStandalone();
            autoOpened.handleServerResponse(payload);
        }
    }

    private static void handleMail(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            MailScreen.openStandalone();
            return;
        }

        if (minecraft.screen instanceof MailScreen mailScreen) {
            mailScreen.handleServerResponse(payload);
            return;
        }

        if (shouldAutoOpenJsonScreen(payload)) {
            MailScreen autoOpened = MailScreen.openStandalone();
            autoOpened.handleServerResponse(payload);
        }
    }

    private static void handleInvest(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            InvestScreen.openStandalone();
            return;
        }

        if (minecraft.screen instanceof InvestScreen investScreen) {
            investScreen.handleServerResponse(payload);
            return;
        }

        if (shouldAutoOpenJsonScreen(payload)) {
            InvestScreen autoOpened = InvestScreen.openStandalone();
            autoOpened.handleServerResponse(payload);
        }
    }

    private static void handleStatus(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            StatusScreen.openStandalone();
            return;
        }

        if (minecraft.screen instanceof StatusScreen statusScreen) {
            statusScreen.handleServerResponse(payload);
            return;
        }

        if (shouldAutoOpenJsonScreen(payload)) {
            StatusScreen autoOpened = StatusScreen.openStandalone();
            autoOpened.handleServerResponse(payload);
        }
    }

    private static void handlePlayer(UiResponsePayload payload, Minecraft minecraft) {
        if (payload.action() == UiAction.OPEN) {
            PlayerOverviewScreen.openStandalone();
            return;
        }

        if (minecraft.screen instanceof PlayerOverviewScreen playerOverviewScreen) {
            playerOverviewScreen.handleServerResponse(payload);
            return;
        }

        if (shouldAutoOpenJsonScreen(payload)) {
            PlayerOverviewScreen autoOpened = PlayerOverviewScreen.openStandalone();
            autoOpened.handleServerResponse(payload);
        }
    }

    private static boolean shouldAutoOpenJsonScreen(UiResponsePayload payload) {
        return payload.success() && hasJsonData(payload);
    }

    private static boolean hasJsonData(UiResponsePayload payload) {
        return payload.dataJson() != null && !payload.dataJson().isBlank();
    }
}
