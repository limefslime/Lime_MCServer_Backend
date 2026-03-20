package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewData;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewFormatter;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewParser;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class PlayerOverviewScreen extends BaseTabbedScreen {
    private static String lastSelectedTab = PlayerOverviewFormatter.TAB_WALLET;

    private final Screen returnScreen;
    private final Map<UiAction, String> pendingRequestIds = new HashMap<>();

    private UiListPanel listPanel;
    private List<Component> detailLines = List.of();

    private PlayerOverviewData.WalletSnapshot walletData = PlayerOverviewData.WalletSnapshot.empty();
    private PlayerOverviewData.ActivitySnapshot activityData = PlayerOverviewData.ActivitySnapshot.empty();
    private PlayerOverviewData.SummarySnapshot summaryData = PlayerOverviewData.SummarySnapshot.empty();

    private boolean walletLoaded;
    private boolean activityLoaded;
    private boolean summaryLoaded;
    private boolean partialOverview;
    private boolean loading;

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

    public PlayerOverviewScreen(@Nullable Screen returnScreen) {
        super(Component.translatable("screen.namanseulfarming.player.title"));
        this.returnScreen = returnScreen;
        registerTabs();
        setInitialTab(lastSelectedTab);
    }

    public static PlayerOverviewScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlayerOverviewScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        PlayerOverviewScreen screen = new PlayerOverviewScreen(parent);
        minecraft.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();
        initCommonButtons(frameX + frameWidth - 4, frameY + 8);
        initRefreshButton(frameX + frameWidth - 4, frameY + 8);
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.player.back"));
        }

        int tabGap = 4;
        int tabCount = 3;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 56, 108);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);

        listPanel = new UiListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);
        updateTabContent();
        requestOverview(false);
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
    protected void onRefreshPressed() {
        requestOverview(true);
    }

    @Override
    protected void onTabChanged(String tabId) {
        lastSelectedTab = tabId;
        if (PlayerOverviewFormatter.TAB_WALLET.equals(tabId) && !walletLoaded) {
            requestWallet(false);
        } else if (PlayerOverviewFormatter.TAB_ACTIVITY.equals(tabId) && !activityLoaded) {
            requestActivity(false);
        } else if (PlayerOverviewFormatter.TAB_SUMMARY.equals(tabId) && !summaryLoaded) {
            requestSummary(false);
        }
        updateTabContent();
    }

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload.action() == UiAction.OPEN) {
            return;
        }

        String pendingRequestId = pendingRequestIds.get(payload.action());
        if (pendingRequestId != null && !pendingRequestId.equals(payload.requestId())) {
            return;
        }
        pendingRequestIds.remove(payload.action());
        loading = !pendingRequestIds.isEmpty();
        if (!loading) {
            setLoading(false);
        }

        if (!payload.success()) {
            setError(payload.error() == null || payload.error().isBlank()
                    ? "Unable to load player overview."
                    : payload.error());
            updateActionButtons();
            return;
        }

        try {
            switch (payload.action()) {
                case PLAYER_OVERVIEW, PLAYER_REFRESH -> applyOverview(payload.dataJson());
                case PLAYER_WALLET -> {
                    walletData = PlayerOverviewParser.parseWallet(payload.dataJson());
                    walletLoaded = true;
                }
                case PLAYER_ACTIVITY -> {
                    activityData = PlayerOverviewParser.parseActivity(payload.dataJson());
                    activityLoaded = true;
                }
                case PLAYER_SUMMARY -> {
                    summaryData = PlayerOverviewParser.parseSummary(payload.dataJson());
                    summaryLoaded = true;
                }
                default -> {
                    // ignore unrelated actions
                }
            }

            setError(null);
            updateTabContent();
        } catch (Exception ex) {
            setError("Unable to read player overview data.");
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
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.player.summary"), summaryX + 6, summaryY + 6);
        renderClipped(graphics, summaryX, summaryY, summaryWidth, summaryHeight, () -> renderSummaryLines(graphics));

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.player.list"), listX + 6, listY + 6);
        if (listPanel != null) {
            listPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.player.detail"), detailX + 6, detailY + 6);
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
        addTab(PlayerOverviewFormatter.TAB_WALLET, Component.translatable("screen.namanseulfarming.player.tab.wallet"));
        addTab(PlayerOverviewFormatter.TAB_ACTIVITY, Component.translatable("screen.namanseulfarming.player.tab.activity"));
        addTab(PlayerOverviewFormatter.TAB_SUMMARY, Component.translatable("screen.namanseulfarming.player.tab.summary"));
    }

    private void requestOverview(boolean forceRefresh) {
        loading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.player.loading"));
        setError(null);

        String requestId = forceRefresh
                ? UiClientNetworking.requestPlayerRefresh()
                : UiClientNetworking.requestPlayerOverview(false);
        pendingRequestIds.put(forceRefresh ? UiAction.PLAYER_REFRESH : UiAction.PLAYER_OVERVIEW, requestId);
        updateActionButtons();
    }

    private void requestWallet(boolean forceRefresh) {
        loading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.player.loading"));
        setError(null);
        String requestId = UiClientNetworking.requestPlayerWallet(forceRefresh);
        pendingRequestIds.put(UiAction.PLAYER_WALLET, requestId);
        updateActionButtons();
    }

    private void requestActivity(boolean forceRefresh) {
        loading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.player.loading"));
        setError(null);
        String requestId = UiClientNetworking.requestPlayerActivity(forceRefresh);
        pendingRequestIds.put(UiAction.PLAYER_ACTIVITY, requestId);
        updateActionButtons();
    }

    private void requestSummary(boolean forceRefresh) {
        loading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.player.loading"));
        setError(null);
        String requestId = UiClientNetworking.requestPlayerSummary(forceRefresh);
        pendingRequestIds.put(UiAction.PLAYER_SUMMARY, requestId);
        updateActionButtons();
    }

    private void applyOverview(String json) {
        PlayerOverviewData overview = PlayerOverviewParser.parseOverview(json);
        walletData = overview.wallet();
        activityData = overview.activity();
        summaryData = overview.summary();
        partialOverview = overview.partial();

        walletLoaded = true;
        activityLoaded = true;
        summaryLoaded = true;
    }

    private void updateTabContent() {
        if (listPanel == null) {
            return;
        }

        List<Component> entries = PlayerOverviewFormatter.buildListEntries(
                activeTabId(),
                walletData,
                activityData,
                summaryData
        );
        listPanel.setEntries(entries);
        if (entries.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.player.empty"));
        } else {
            setEmpty(null);
        }
        updateDetailLines();
    }

    private void updateDetailLines() {
        int selectedIndex = listPanel == null ? 0 : listPanel.selectedIndex();
        detailLines = PlayerOverviewFormatter.buildDetailLines(
                activeTabId(),
                walletData,
                activityData,
                summaryData,
                selectedIndex
        );
    }

    private void renderSummaryLines(GuiGraphics graphics) {
        List<Component> summaryLines = PlayerOverviewFormatter.buildOverviewHighlights(
                walletData,
                activityData,
                summaryData,
                partialOverview
        );

        int lineY = summaryY + 20;
        int maxY = summaryY + summaryHeight - 10;
        for (Component line : summaryLines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line, summaryX + 8, lineY, 0xDDE6F9, false);
            lineY += 12;
        }
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
            refreshButton.active = !loading;
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
