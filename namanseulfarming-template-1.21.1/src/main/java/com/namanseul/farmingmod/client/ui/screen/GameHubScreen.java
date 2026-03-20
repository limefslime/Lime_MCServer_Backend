package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.tab.HubTabView;
import com.namanseul.farmingmod.client.ui.tab.InvestTabView;
import com.namanseul.farmingmod.client.ui.tab.MailTabView;
import com.namanseul.farmingmod.client.ui.tab.PlayerTabView;
import com.namanseul.farmingmod.client.ui.tab.RegionTabView;
import com.namanseul.farmingmod.client.ui.tab.ShopTabView;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.client.ui.widget.UiMessageBanner;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static String lastSelectedTab = TAB_SHOP;

    private final Map<String, HubTabView> tabViews = new LinkedHashMap<>();

    private HubSummaryData summaryData;
    private UiListPanel listPanel;
    private List<Component> detailLines = List.of();
    private String pendingRequestId;
    private Button openTabButton;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private int summaryX;
    private int summaryY;
    private int summaryWidth;
    private int summaryHeight;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    private int detailX;
    private int detailY;
    private int detailWidth;
    private int detailHeight;

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
                Component.translatable("screen.namanseulfarming.shop.open_button"),
                0,
                0,
                92,
                20,
                button -> openActiveTabScreen()
        ));
        int tabGap = 4;
        int tabCount = 5;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 48, 78);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);
        layoutTopButtons();
        listPanel = new UiListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);

        updateTabContent();
        refreshOpenShopButton();
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
    protected void onRefreshPressed() {
        requestSummary(UiAction.REFRESH);
    }

    @Override
    protected void onTabChanged(String tabId) {
        lastSelectedTab = tabId;
        refreshOpenShopButton();
        updateTabContent();
    }

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload.action() == UiAction.OPEN) {
            return;
        }

        if (pendingRequestId != null && !pendingRequestId.equals(payload.requestId())) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.debug("[UI] ignored stale response id={} pending={}", payload.requestId(), pendingRequestId);
            }
            return;
        }

        pendingRequestId = null;
        setLoading(false);

        if (!payload.success()) {
            setError(payload.error() == null ? "Hub summary failed" : payload.error());
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.hub.banner.failed")));
            return;
        }

        if (payload.data() == null) {
            setError("Hub summary response had no data");
            return;
        }

        summaryData = payload.data();
        setError(null);
        updateTabContent();

        if (summaryData.partial()) {
            String note = summaryData.partialNote() == null || summaryData.partialNote().isBlank()
                    ? "partial summary"
                    : summaryData.partialNote();
            setMessageBanner(new UiMessageBanner(
                    UiMessageBanner.MessageType.WARNING,
                    Component.literal("Some summary sections failed: " + userFacingPartialNote(note))
            ));
        } else {
            setMessageBanner(new UiMessageBanner(
                    UiMessageBanner.MessageType.INFO,
                    Component.translatable("screen.namanseulfarming.hub.banner.updated", formatTime(summaryData.refreshedAtEpochMillis()))
            ));
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        layoutTopButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, summaryX, summaryY, summaryWidth, summaryHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.hub.summary"), summaryX + 6, summaryY + 6);
        renderClipped(graphics, summaryX, summaryY, summaryWidth, summaryHeight, () -> renderSummaryLines(graphics));

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        if (listPanel != null) {
            listPanel.render(graphics, font, mouseX, mouseY);
        }
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.hub.list"), listX + 6, listY + 6);

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.hub.detail"), detailX + 6, detailY + 6);
        renderClipped(graphics, detailX, detailY, detailWidth, detailHeight, () -> renderDetailLines(graphics));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listPanel != null && listPanel.mouseClicked(mouseX, mouseY, button)) {
            updateDetailLines();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listPanel != null && listPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        frameHeight = Math.min(320, height - 24);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        summaryX = frameX + 10;
        summaryY = frameY + 58;
        summaryWidth = frameWidth - 20;
        summaryHeight = 62;

        listX = frameX + 10;
        listY = summaryY + summaryHeight + 6;
        int innerWidth = frameWidth - 20;
        int preferredListWidth = clampInt(innerWidth * 34 / 100, 108, 176);
        listWidth = Math.max(96, preferredListWidth);
        listHeight = Math.max(60, frameY + frameHeight - 10 - listY);

        detailX = listX + listWidth + 8;
        detailY = listY;
        detailWidth = Math.max(90, innerWidth - listWidth - 8);
        detailHeight = listHeight;
    }

    private void layoutTopButtons() {
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        if (openTabButton == null) {
            return;
        }
        int openWidth = clampInt(frameWidth / 4, 64, 100);
        int refreshX = frameX + frameWidth - ((72 * 2) + 6 + 2);
        int openX = refreshX - 6 - openWidth;
        if (openX < frameX + 10) {
            openTabButton.visible = false;
            openTabButton.active = false;
            return;
        }
        openTabButton.visible = true;
        openTabButton.setPosition(openX, frameY + 8);
        openTabButton.setWidth(openWidth);
    }

    private void updateTabContent() {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null || listPanel == null) {
            return;
        }

        listPanel.setEntries(tabView.buildListEntries(summaryData));
        if (listPanel.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.hub.empty"));
        } else {
            setEmpty(null);
        }
        updateDetailLines();
    }

    private void refreshOpenShopButton() {
        if (openTabButton == null) {
            return;
        }

        if (TAB_SHOP.equals(activeTabId())) {
            openTabButton.visible = true;
            openTabButton.active = true;
            openTabButton.setMessage(Component.translatable("screen.namanseulfarming.shop.open_button"));
            return;
        }

        if (TAB_MAIL.equals(activeTabId())) {
            openTabButton.visible = true;
            openTabButton.active = true;
            openTabButton.setMessage(Component.translatable("screen.namanseulfarming.mail.open_button"));
            return;
        }

        if (TAB_INVEST.equals(activeTabId())) {
            openTabButton.visible = true;
            openTabButton.active = true;
            openTabButton.setMessage(Component.translatable("screen.namanseulfarming.invest.open_button"));
            return;
        }

        if (TAB_REGION.equals(activeTabId())) {
            openTabButton.visible = true;
            openTabButton.active = true;
            openTabButton.setMessage(Component.translatable("screen.namanseulfarming.status.open_button"));
            return;
        }

        if (TAB_PLAYER.equals(activeTabId())) {
            openTabButton.visible = true;
            openTabButton.active = true;
            openTabButton.setMessage(Component.translatable("screen.namanseulfarming.player.open_button"));
            return;
        }

        openTabButton.visible = false;
        openTabButton.active = false;
    }

    private void updateDetailLines() {
        HubTabView tabView = tabViews.get(activeTabId());
        if (tabView == null || listPanel == null) {
            detailLines = List.of();
            return;
        }
        detailLines = tabView.buildDetailLines(summaryData, listPanel.selectedIndex());
    }

    private void renderSummaryLines(GuiGraphics graphics) {
        if (summaryData == null) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.hub.summary_waiting"),
                    summaryX + 8, summaryY + 24, 0xDDE6F9, false);
            return;
        }

        int lineX = summaryX + 8;
        int lineY = summaryY + 20;
        int color = 0xDDE6F9;
        int contentWidth = summaryWidth - 16;
        if (contentWidth >= 320) {
            int columnTwoX = lineX + contentWidth / 2;
            graphics.drawString(font, Component.literal("Focus: " + summaryData.currentFocusRegion()), lineX, lineY, color, false);
            graphics.drawString(font, Component.literal("Active Events: " + summaryData.activeEventCount()), columnTwoX, lineY, color, false);
            graphics.drawString(font, Component.literal("Project Effects: " + summaryData.activeProjectEffectCount()), lineX, lineY + 12, color, false);
            graphics.drawString(font, Component.literal("Dominant Region: " + summaryData.dominantRegionCategory()), columnTwoX, lineY + 12, color, false);
            graphics.drawString(font, Component.literal("Generated: " + formatTime(summaryData.generatedAtEpochMillis())), lineX, lineY + 24, color, false);
            graphics.drawString(font, Component.literal("Refreshed: " + formatTime(summaryData.refreshedAtEpochMillis())), columnTwoX, lineY + 24, color, false);
            return;
        }

        graphics.drawString(font, Component.literal("Focus: " + summaryData.currentFocusRegion()), lineX, lineY, color, false);
        graphics.drawString(font, Component.literal("Active Events: " + summaryData.activeEventCount()), lineX, lineY + 12, color, false);
        graphics.drawString(font, Component.literal("Project Effects: " + summaryData.activeProjectEffectCount()), lineX, lineY + 24, color, false);
        graphics.drawString(font, Component.literal("Dominant Region: " + summaryData.dominantRegionCategory()), lineX, lineY + 36, color, false);
        int rightX = lineX + Math.max(120, contentWidth / 2);
        graphics.drawString(font, Component.literal("Generated: " + formatTime(summaryData.generatedAtEpochMillis())), rightX, lineY + 24, color, false);
        graphics.drawString(font, Component.literal("Refreshed: " + formatTime(summaryData.refreshedAtEpochMillis())), rightX, lineY + 36, color, false);
    }

    private void renderDetailLines(GuiGraphics graphics) {
        int lineY = detailY + 22;
        for (Component line : detailLines) {
            graphics.drawString(font, line, detailX + 8, lineY, 0xEAF1FF, false);
            lineY += 12;
            if (lineY > detailY + detailHeight - 10) {
                break;
            }
        }
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String userFacingPartialNote(String rawNote) {
        if (rawNote == null || rawNote.isBlank()) {
            return "partial summary";
        }
        return switch (rawNote) {
            case "ops summary connect failed" -> "ops summary connect failed (backend not reachable)";
            case "ops summary timeout" -> "ops summary timeout (backend too slow)";
            default -> rawNote;
        };
    }
}
