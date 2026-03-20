package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.tab.HubTabView;
import com.namanseul.farmingmod.client.ui.tab.InvestTabView;
import com.namanseul.farmingmod.client.ui.tab.MailTabView;
import com.namanseul.farmingmod.client.ui.tab.PlayerTabView;
import com.namanseul.farmingmod.client.ui.tab.RegionTabView;
import com.namanseul.farmingmod.client.ui.tab.ShopTabView;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class GameHubScreen extends BaseTabbedScreen {
    private static final String TAB_SHOP = "shop";
    private static final String TAB_MAIL = "mail";
    private static final String TAB_INVEST = "invest";
    private static final String TAB_REGION = "region";
    private static final String TAB_PLAYER = "player";

    private static String lastSelectedTab = TAB_SHOP;

    private final Map<String, HubTabView> tabViews = new LinkedHashMap<>();

    private HubSummaryData summaryData;
    private List<Component> summaryLines = List.of();
    private String pendingRequestId;
    private Button openTabButton;
    private boolean summaryPartial;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private int actionX;
    private int actionY;
    private int actionWidth;
    private int actionHeight;

    private int summaryX;
    private int summaryY;
    private int summaryWidth;
    private int summaryHeight;

    public GameHubScreen() {
        super(Component.translatable("screen.namanseulfarming.hub.title"));
        registerTabs();
        setInitialTab(lastSelectedTab);
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

        openTabButton = addRenderableWidget(UiButton.create(
                Component.literal("Open"),
                0,
                0,
                108,
                20,
                button -> openActiveTabScreen()
        ));

        int tabGap = 4;
        int tabCount = 5;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 48, 78);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);

        updateTabContent();
        layoutButtons();

        if (summaryData == null) {
            requestSummary(UiAction.INIT);
        }
    }

    private void registerTabs() {
        if (!tabViews.isEmpty()) {
            return;
        }

        tabViews.put(TAB_SHOP, new ShopTabView());
        tabViews.put(TAB_MAIL, new MailTabView());
        tabViews.put(TAB_INVEST, new InvestTabView());
        tabViews.put(TAB_REGION, new RegionTabView());
        tabViews.put(TAB_PLAYER, new PlayerTabView());

        addTab(TAB_SHOP, tabViews.get(TAB_SHOP).tabLabel());
        addTab(TAB_MAIL, tabViews.get(TAB_MAIL).tabLabel());
        addTab(TAB_INVEST, tabViews.get(TAB_INVEST).tabLabel());
        addTab(TAB_REGION, tabViews.get(TAB_REGION).tabLabel());
        addTab(TAB_PLAYER, tabViews.get(TAB_PLAYER).tabLabel());
    }

    @Override
    protected void onTabChanged(String tabId) {
        lastSelectedTab = tabId;
        updateTabContent();
        layoutButtons();
    }

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload.action() == UiAction.OPEN) {
            return;
        }

        if (pendingRequestId != null && !pendingRequestId.equals(payload.requestId())) {
            return;
        }

        pendingRequestId = null;
        setLoading(false);

        if (!payload.success()) {
            String message = payload.error() == null || payload.error().isBlank()
                    ? "Unable to load hub overview."
                    : payload.error();
            setError(message);
            return;
        }

        if (payload.data() == null) {
            setError("Hub overview returned no data.");
            return;
        }

        summaryData = payload.data();
        summaryPartial = summaryData.partial();
        setError(null);
        updateTabContent();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        layoutButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.literal("Next Action"), actionX + 6, actionY + 6);
        renderActiveAction(graphics);

        renderPanel(graphics, summaryX, summaryY, summaryWidth, summaryHeight);
        renderSectionTitle(graphics, Component.literal("Quick Summary"), summaryX + 6, summaryY + 6);
        renderClipped(graphics, summaryX, summaryY, summaryWidth, summaryHeight, () -> renderSummaryLines(graphics));
    }

    private void requestSummary(UiAction action) {
        setLoading(true, Component.translatable("screen.namanseulfarming.hub.loading"));
        setError(null);
        pendingRequestId = UiClientNetworking.requestHub(action);
    }

    private void openActiveTabScreen() {
        if (TAB_SHOP.equals(activeTabId())) {
            Minecraft.getInstance().setScreen(new ShopScreen(this));
            return;
        }
        if (TAB_MAIL.equals(activeTabId())) {
            Minecraft.getInstance().setScreen(new MailScreen(this));
            return;
        }
        if (TAB_INVEST.equals(activeTabId())) {
            Minecraft.getInstance().setScreen(new InvestScreen(this));
            return;
        }
        if (TAB_REGION.equals(activeTabId())) {
            Minecraft.getInstance().setScreen(new StatusScreen(this));
            return;
        }
        if (TAB_PLAYER.equals(activeTabId())) {
            Minecraft.getInstance().setScreen(new PlayerOverviewScreen(this));
        }
    }

    private void recalcLayout() {
        frameWidth = Math.min(560, width - 20);
        frameHeight = Math.min(300, height - 24);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int contentX = frameX + 10;
        int contentWidth = frameWidth - 20;

        actionX = contentX;
        actionY = frameY + 58;
        actionWidth = contentWidth;
        actionHeight = 84;

        summaryX = contentX;
        summaryY = actionY + actionHeight + 8;
        summaryWidth = contentWidth;
        summaryHeight = Math.max(68, frameY + frameHeight - 10 - summaryY);
    }

    private void layoutButtons() {
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);

        if (openTabButton == null) {
            return;
        }

        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null) {
            openTabButton.visible = false;
            openTabButton.active = false;
            return;
        }

        int openWidth = clampInt(actionWidth / 3, 92, 136);
        int openX = actionX + actionWidth - openWidth - 8;
        int openY = actionY + actionHeight - 28;

        openTabButton.visible = true;
        openTabButton.active = true;
        openTabButton.setPosition(openX, openY);
        openTabButton.setWidth(openWidth);
        openTabButton.setHeight(20);
        openTabButton.setMessage(tabView.openButtonLabel());
    }

    private void updateTabContent() {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null) {
            summaryLines = List.of();
            setEmpty(Component.literal("No tab selected."));
            return;
        }

        if (summaryData == null) {
            summaryLines = List.of(Component.literal("Loading hub overview..."));
            setEmpty(null);
            return;
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Focus now: " + safe(summaryData.currentFocusRegion())));
        lines.add(Component.literal("Active events to watch: " + summaryData.activeEventCount()));
        lines.addAll(tabView.summaryLines(summaryData));
        if (summaryPartial) {
            lines.add(Component.literal("Some overview values are still syncing."));
        }

        summaryLines = lines;
        if (summaryLines.isEmpty()) {
            setEmpty(Component.literal("No overview available."));
        } else {
            setEmpty(null);
        }
    }

    private void renderActiveAction(GuiGraphics graphics) {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null) {
            graphics.drawString(font, Component.literal("Select a tab."), actionX + 8, actionY + 26, 0xDDE6F9, false);
            return;
        }

        int textX = actionX + 8;
        int textY = actionY + 24;

        graphics.drawString(font, Component.literal("Selected: " + tabView.tabLabel().getString()), textX, textY, 0xFFFFFF, false);
        graphics.drawString(font, tabView.actionTitle(), textX, textY + 14, 0xEAF1FF, false);
        graphics.drawString(font, tabView.actionHint(), textX, textY + 28, 0xBFD0E8, false);
    }

    private void renderSummaryLines(GuiGraphics graphics) {
        int lineY = summaryY + 22;
        for (Component line : summaryLines) {
            graphics.drawString(font, line, summaryX + 8, lineY, 0xEAF1FF, false);
            lineY += 12;
            if (lineY > summaryY + summaryHeight - 10) {
                break;
            }
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
