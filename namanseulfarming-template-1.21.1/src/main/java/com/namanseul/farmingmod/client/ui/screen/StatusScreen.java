package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.status.StatusJsonParser;
import com.namanseul.farmingmod.client.ui.status.StatusOverviewData;
import com.namanseul.farmingmod.client.ui.status.StatusViewFormatter;
import com.namanseul.farmingmod.client.ui.widget.UiMessageBanner;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class StatusScreen extends BaseTabbedScreen {
    private static String lastSelectedTab = StatusViewFormatter.TAB_REGION;

    private final Screen returnScreen;

    private StatusOverviewData overviewData;
    private com.namanseul.farmingmod.client.ui.widget.UiListPanel listPanel;
    private List<Component> detailLines = List.of();
    private String pendingRequestId;
    private boolean listLoading;

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

    public StatusScreen(@Nullable Screen returnScreen) {
        super(Component.translatable("screen.namanseulfarming.status.title"));
        this.returnScreen = returnScreen;
        registerTabs();
        setInitialTab(lastSelectedTab);
    }

    public static StatusScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof StatusScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        StatusScreen screen = new StatusScreen(parent);
        minecraft.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();
        initCommonButtons(frameX + frameWidth - 4, frameY + 8);
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.status.back"));
        }

        int tabGap = 4;
        int tabCount = 4;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 52, 90);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);
        listPanel = new com.namanseul.farmingmod.client.ui.widget.UiListPanel(
                listX + 4,
                listY + 18,
                listWidth - 8,
                listHeight - 22
        );
        updateTabContent();
        requestOverview(false);
    }

    @Override
    protected void onRefreshPressed() {
        requestOverview(true);
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        if (returnScreen != null) {
            minecraft.setScreen(returnScreen);
            return;
        }
        minecraft.setScreen(new GameHubScreen());
    }

    @Override
    protected void onTabChanged(String tabId) {
        lastSelectedTab = tabId;
        updateTabContent();
    }

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload.action() != UiAction.STATUS_OVERVIEW && payload.action() != UiAction.STATUS_REFRESH) {
            return;
        }
        if (pendingRequestId != null && !pendingRequestId.equals(payload.requestId())) {
            return;
        }

        pendingRequestId = null;
        listLoading = false;
        setLoading(false);

        if (!payload.success()) {
            setError(payload.error() == null ? "status request failed" : payload.error());
            setMessageBanner(new UiMessageBanner(
                    UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.status.banner.failed")
            ));
            updateActionButtons();
            return;
        }

        try {
            overviewData = StatusJsonParser.parseOverview(payload.dataJson());
            setError(null);
            updateTabContent();

            if (overviewData.partial()) {
                String note = overviewData.partialNotes().isEmpty()
                        ? "partial response"
                        : overviewData.partialNotes().get(0);
                setMessageBanner(new UiMessageBanner(
                        UiMessageBanner.MessageType.WARNING,
                        Component.literal("Some sections failed: " + note)
                ));
            } else {
                setMessageBanner(new UiMessageBanner(
                        UiMessageBanner.MessageType.INFO,
                        Component.translatable("screen.namanseulfarming.status.banner.loaded")
                ));
            }
        } catch (Exception ex) {
            setError("failed to parse status response");
            setMessageBanner(new UiMessageBanner(
                    UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.status.banner.failed")
            ));
        }

        updateActionButtons();
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        updateActionButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, summaryX, summaryY, summaryWidth, summaryHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.status.summary"), summaryX + 6, summaryY + 6);
        renderClipped(graphics, summaryX, summaryY, summaryWidth, summaryHeight, () -> renderSummaryLines(graphics));

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.status.list"), listX + 6, listY + 6);
        if (listPanel != null) {
            listPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.status.detail"), detailX + 6, detailY + 6);
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

    private void registerTabs() {
        addTab(StatusViewFormatter.TAB_FOCUS, Component.translatable("screen.namanseulfarming.status.tab.focus"));
        addTab(StatusViewFormatter.TAB_REGION, Component.translatable("screen.namanseulfarming.status.tab.region"));
        addTab(StatusViewFormatter.TAB_EVENT, Component.translatable("screen.namanseulfarming.status.tab.event"));
        addTab(StatusViewFormatter.TAB_COMPLETION, Component.translatable("screen.namanseulfarming.status.tab.completion"));
    }

    private void requestOverview(boolean forceRefresh) {
        listLoading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.status.loading"));
        setError(null);
        pendingRequestId = forceRefresh
                ? UiClientNetworking.requestStatusRefresh()
                : UiClientNetworking.requestStatusOverview(false);
        updateActionButtons();
    }

    private void updateTabContent() {
        if (listPanel == null) {
            return;
        }

        List<Component> entries = StatusViewFormatter.buildListEntries(overviewData, activeTabId());
        listPanel.setEntries(entries);
        if (entries.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.status.empty"));
        } else {
            setEmpty(null);
        }
        updateDetailLines();
    }

    private void updateDetailLines() {
        int selectedIndex = listPanel == null ? 0 : listPanel.selectedIndex();
        detailLines = StatusViewFormatter.buildDetailLines(overviewData, activeTabId(), selectedIndex);
    }

    private void renderSummaryLines(GuiGraphics graphics) {
        if (overviewData == null) {
            graphics.drawString(font, Component.translatable("screen.namanseulfarming.status.summary_waiting"),
                    summaryX + 8, summaryY + 24, 0xDDE6F9, false);
            return;
        }

        int x = summaryX + 8;
        int y = summaryY + 20;
        int color = 0xDDE6F9;
        int contentWidth = summaryWidth - 16;

        String focusRegion = "-";
        if (overviewData.focus() != null && overviewData.focus().has("focusRegion")) {
            try {
                focusRegion = overviewData.focus().get("focusRegion").getAsString();
            } catch (Exception ignored) {
                // keep fallback
            }
        }

        if (contentWidth >= 320) {
            int rightX = x + contentWidth / 2;
            graphics.drawString(font, Component.literal("Focus: " + focusRegion), x, y, color, false);
            graphics.drawString(font, Component.literal("Regions: " + overviewData.regionCount()), rightX, y, color, false);
            graphics.drawString(font, Component.literal("Active Events: " + overviewData.activeEventCount()), x, y + 12, color, false);
            graphics.drawString(font, Component.literal("Project Effects: " + overviewData.activeProjectEffectCount()), rightX, y + 12, color, false);
            graphics.drawString(font, Component.literal("Completed Projects: " + overviewData.completedProjectCount()), x, y + 24, color, false);
            graphics.drawString(font, Component.literal("Partial: " + overviewData.partial()), rightX, y + 24, color, false);
            return;
        }

        graphics.drawString(font, Component.literal("Focus: " + focusRegion), x, y, color, false);
        graphics.drawString(font, Component.literal("Regions: " + overviewData.regionCount()), x, y + 12, color, false);
        graphics.drawString(font, Component.literal("Active Events: " + overviewData.activeEventCount()), x, y + 24, color, false);
        graphics.drawString(font, Component.literal("Project Effects: " + overviewData.activeProjectEffectCount()), x, y + 36, color, false);
        int rightX = x + Math.max(120, contentWidth / 2);
        graphics.drawString(font, Component.literal("Completed Projects: " + overviewData.completedProjectCount()), rightX, y + 24, color, false);
        graphics.drawString(font, Component.literal("Partial: " + overviewData.partial()), rightX, y + 36, color, false);
    }

    private void renderDetailLines(GuiGraphics graphics) {
        int lineY = detailY + 22;
        int maxY = detailY + detailHeight - 10;
        for (Component line : detailLines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line, detailX + 8, lineY, 0xEAF1FF, false);
            lineY += 12;
        }
    }

    private void updateActionButtons() {
        if (refreshButton != null) {
            refreshButton.active = !listLoading;
        }
        if (closeButton != null) {
            closeButton.active = true;
        }
    }

    private void recalcLayout() {
        frameWidth = Math.min(560, width - 20);
        frameHeight = Math.min(360, height - 36);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        summaryX = frameX + 10;
        summaryY = frameY + 58;
        summaryWidth = frameWidth - 20;
        summaryHeight = 62;

        listX = frameX + 10;
        listY = summaryY + summaryHeight + 6;
        int innerWidth = frameWidth - 20;
        int preferredListWidth = clampInt(innerWidth * 36 / 100, 108, 194);
        listWidth = Math.max(96, preferredListWidth);
        listHeight = Math.max(60, frameY + frameHeight - 10 - listY);

        detailX = listX + listWidth + 8;
        detailY = listY;
        detailWidth = Math.max(90, innerWidth - listWidth - 8);
        detailHeight = listHeight;
    }
}
