package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.ui.tab.HubTabView;
import com.namanseul.farmingmod.client.ui.tab.InvestTabView;
import com.namanseul.farmingmod.client.ui.tab.MailTabView;
import com.namanseul.farmingmod.client.ui.tab.PlayerTabView;
import com.namanseul.farmingmod.client.ui.tab.RegionTabView;
import com.namanseul.farmingmod.client.ui.tab.ShopTabView;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class GameHubScreen extends BaseGameScreen {
    private static final String MENU_SHOP = "shop";
    private static final String MENU_MAIL = "mail";
    private static final String MENU_INVEST = "invest";
    private static final String MENU_REGION = "region";
    private static final String MENU_PLAYER = "player";

    private static final int MENU_BUTTON_HEIGHT = 22;
    private static final int MENU_BUTTON_GAP = 8;
    private static final String HUB_MENU_ERROR_MESSAGE = "Unable to open hub menu.";

    private final Map<String, HubTabView> menuViews = new LinkedHashMap<>();
    private final Map<String, Button> menuButtons = new LinkedHashMap<>();

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private int menuX;
    private int menuY;
    private int menuWidth;
    private int menuHeight;

    public GameHubScreen() {
        super(Component.translatable("screen.namanseulfarming.hub.title"));
        registerMenus();
    }

    public static void openFromCommand() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new GameHubScreen());
    }

    public static void openFromKeybind() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GameHubScreen) {
            return;
        }
        minecraft.setScreen(new GameHubScreen());
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();
        initCommonButtons(frameX + frameWidth - 4, frameY + 8);
        initMenuButtons();
        setLoading(false);
        setError(null);
        setEmpty(null);
    }

    private void registerMenus() {
        if (!menuViews.isEmpty()) {
            return;
        }

        menuViews.put(MENU_SHOP, new ShopTabView());
        menuViews.put(MENU_MAIL, new MailTabView());
        menuViews.put(MENU_INVEST, new InvestTabView());
        menuViews.put(MENU_REGION, new RegionTabView());
        menuViews.put(MENU_PLAYER, new PlayerTabView());
    }

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload == null || !payload.success()) {
            setError(HUB_MENU_ERROR_MESSAGE);
            return;
        }

        setError(null);
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);
        renderPanel(graphics, menuX, menuY, menuWidth, menuHeight);
    }

    private void recalcLayout() {
        frameWidth = Math.min(340, width - 20);
        frameHeight = Math.min(260, height - 24);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        menuX = frameX + 10;
        menuY = frameY + 34;
        menuWidth = frameWidth - 20;
        menuHeight = Math.max(84, frameHeight - 44);
    }

    private void initMenuButtons() {
        for (Button existingButton : menuButtons.values()) {
            removeWidget(existingButton);
        }
        menuButtons.clear();

        int buttonWidth = Math.min(220, Math.max(140, menuWidth - 24));
        int baseX = menuX + (menuWidth - buttonWidth) / 2;
        int baseY = menuY + 10;

        int index = 0;
        for (Map.Entry<String, HubTabView> entry : menuViews.entrySet()) {
            int x = baseX;
            int y = baseY + index * (MENU_BUTTON_HEIGHT + MENU_BUTTON_GAP);

            final String menuId = entry.getKey();
            Button button = addRenderableWidget(UiButton.create(
                    entry.getValue().menuLabel(),
                    x,
                    y,
                    buttonWidth,
                    MENU_BUTTON_HEIGHT,
                    pressed -> openMenu(menuId)
            ));
            menuButtons.put(menuId, button);
            index++;
        }
    }

    private void openMenu(String menuId) {
        HubTabView menuView = menuViews.get(menuId);
        if (menuView == null) {
            return;
        }
        menuView.openFromHub(this);
    }
}
