package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.tab.HubTabView;
import com.namanseul.farmingmod.client.ui.tab.InvestTabView;
import com.namanseul.farmingmod.client.ui.tab.MailTabView;
import com.namanseul.farmingmod.client.ui.tab.PlayerTabView;
import com.namanseul.farmingmod.client.ui.tab.RegionTabView;
import com.namanseul.farmingmod.client.ui.tab.ShopTabView;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private String pendingRequestId;
    private boolean summaryPartial;
    private boolean allowInstantEnter;

    private List<Component> guidanceLines = List.of();
    private List<Component> worldSignalLines = List.of();

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private int guidanceX;
    private int guidanceY;
    private int guidanceWidth;
    private int guidanceHeight;

    private int signalX;
    private int signalY;
    private int signalWidth;
    private int signalHeight;

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

        int tabGap = 4;
        int tabCount = 5;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 48, 78);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);

        updateTabContent();
        if (summaryData == null) {
            requestSummary(UiAction.INIT);
        }
        allowInstantEnter = true;
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
        if (allowInstantEnter) {
            openActiveTabScreen();
        }
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
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, guidanceX, guidanceY, guidanceWidth, guidanceHeight);
        renderSectionTitle(graphics, Component.literal("Next Step"), guidanceX + 6, guidanceY + 6);
        renderClipped(graphics, guidanceX, guidanceY, guidanceWidth, guidanceHeight, () -> renderGuidanceLines(graphics));

        renderPanel(graphics, signalX, signalY, signalWidth, signalHeight);
        renderSectionTitle(graphics, Component.literal("World Signals"), signalX + 6, signalY + 6);
        renderClipped(graphics, signalX, signalY, signalWidth, signalHeight, () -> renderWorldSignalLines(graphics));
    }

    private void requestSummary(UiAction action) {
        setLoading(true, Component.translatable("screen.namanseulfarming.hub.loading"));
        setError(null);
        pendingRequestId = UiClientNetworking.requestHub(action);
    }

    private void openActiveTabScreen() {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null) {
            return;
        }
        tabView.openFromHub(this);
    }

    private void recalcLayout() {
        frameWidth = Math.min(560, width - 20);
        frameHeight = Math.min(300, height - 24);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int contentX = frameX + 10;
        int contentWidth = frameWidth - 20;

        guidanceX = contentX;
        guidanceY = frameY + 58;
        guidanceWidth = contentWidth;
        guidanceHeight = 96;

        signalX = contentX;
        signalY = guidanceY + guidanceHeight + 8;
        signalWidth = contentWidth;
        signalHeight = Math.max(54, frameY + frameHeight - 10 - signalY);
    }

    private void updateTabContent() {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null) {
            guidanceLines = List.of(Component.literal("No tab selected."));
            worldSignalLines = List.of(Component.literal("World signals unavailable."));
            return;
        }

        guidanceLines = buildGuidanceLines(tabView);
        worldSignalLines = buildWorldSignalLines();
        setEmpty(null);
    }

    private List<Component> buildGuidanceLines(HubTabView tabView) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Selected tab: " + tabView.tabLabel().getString()));
        lines.add(Component.literal("Choosing a tab opens that screen immediately."));
        if (summaryData == null) {
            lines.add(Component.literal("Loading tab guidance..."));
            return lines;
        }
        lines.addAll(tabView.buildEntryHints(summaryData));
        return lines;
    }

    private List<Component> buildWorldSignalLines() {
        if (summaryData == null) {
            return List.of(Component.literal("Loading world signals..."));
        }

        List<Component> lines = new ArrayList<>();
        int pendingRewards = Math.max(0, summaryData.unclaimedMailCount());
        int liveEvents = Math.max(0, summaryData.activeEventCount());
        String focusRegion = safe(summaryData.currentFocusRegion());

        if (pendingRewards > 0) {
            lines.add(Component.literal("Mailbox rewards waiting: " + pendingRewards));
        } else {
            lines.add(Component.literal("No pending mailbox rewards."));
        }

        if (liveEvents > 0) {
            lines.add(Component.literal("Live regional events: " + liveEvents));
        } else {
            lines.add(Component.literal("No live regional events now."));
        }

        if (!"-".equals(focusRegion)) {
            lines.add(Component.literal("Current focus region: " + focusRegion));
        }
        if (summaryPartial) {
            lines.add(Component.literal("Some signals are still syncing."));
        }
        return lines;
    }

    private void renderGuidanceLines(GuiGraphics graphics) {
        int lineY = guidanceY + 22;
        for (Component line : guidanceLines) {
            graphics.drawString(font, line, guidanceX + 8, lineY, 0xEAF1FF, false);
            lineY += 12;
            if (lineY > guidanceY + guidanceHeight - 10) {
                break;
            }
        }
    }

    private void renderWorldSignalLines(GuiGraphics graphics) {
        int lineY = signalY + 22;
        for (Component line : worldSignalLines) {
            graphics.drawString(font, line, signalX + 8, lineY, 0xEAF1FF, false);
            lineY += 12;
            if (lineY > signalY + signalHeight - 10) {
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
