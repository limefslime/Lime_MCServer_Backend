package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.invest.InvestStockDetailViewData;
import com.namanseul.farmingmod.client.ui.invest.InvestStockJsonParser;
import com.namanseul.farmingmod.client.ui.invest.InvestStockViewData;
import com.namanseul.farmingmod.client.ui.invest.InvestTradeResultViewData;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestScreen extends BaseGameScreen {
    private final Screen returnScreen;
    private final List<InvestStockViewData> stocks = new ArrayList<>();

    private UiListPanel stockListPanel;
    private InvestStockViewData selectedStock;

    private EditBox quantityInput;
    private Button buyButton;
    private Button sellButton;

    private String pendingListRequestId;
    private String pendingDetailRequestId;
    private String pendingBuyRequestId;
    private String pendingSellRequestId;

    private boolean listLoading;
    private boolean detailLoading;
    private boolean tradeLoading;
    private boolean detailReady;
    private int walletBalance;

    private String inputError;
    private String statusMessage;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;

    private int detailX;
    private int detailY;
    private int detailWidth;
    private int detailHeight;

    private int actionX;
    private int actionY;
    private int actionWidth;
    private int actionHeight;

    private int listPanelX = Integer.MIN_VALUE;
    private int listPanelY = Integer.MIN_VALUE;
    private int listPanelWidth = Integer.MIN_VALUE;
    private int listPanelHeight = Integer.MIN_VALUE;

    public InvestScreen(@Nullable Screen returnScreen) {
        super(Component.literal("Invest"));
        this.returnScreen = returnScreen;
    }

    public static InvestScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof InvestScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        InvestScreen screen = new InvestScreen(parent);
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
            closeButton.setMessage(Component.literal("Back"));
        }

        ensureListPanel();
        initActionWidgets();
        requestStockList(false);
    }

    @Override
    protected void onRefreshPressed() {
        requestStockList(true);
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

    public void handleServerResponse(UiResponsePayload payload) {
        switch (payload.action()) {
            case INVEST_LIST, INVEST_REFRESH -> handleListResponse(payload);
            case INVEST_DETAIL, INVEST_PROGRESS -> handleDetailResponse(payload);
            case INVEST_BUY, INVEST_SELL, INVEST_CONTRIBUTE -> handleTradeResponse(payload);
            default -> {
                // ignore unrelated actions
            }
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        ensureListPanel();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        layoutActionWidgets();
        updateActionButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        renderSectionTitle(graphics, Component.literal("Stocks"), listX + 6, listY + 6);
        if (stockListPanel != null) {
            stockListPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.literal("Detail"), detailX + 6, detailY + 6);
        renderClipped(graphics, detailX, detailY, detailWidth, detailHeight, () -> renderDetailPanel(graphics));

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.literal("Trade"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () -> renderActionPanel(graphics));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (stockListPanel != null && stockListPanel.mouseClicked(mouseX, mouseY, button)) {
            onSelectedStockChanged();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (stockListPanel != null && stockListPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void initActionWidgets() {
        quantityInput = addRenderableWidget(new EditBox(
                font,
                0,
                0,
                80,
                16,
                Component.literal("Quantity")
        ));
        quantityInput.setMaxLength(9);
        quantityInput.setFilter(value -> value.isEmpty() || value.matches("\\d{0,9}"));
        quantityInput.setValue("1");
        quantityInput.setResponder(value -> onQuantityChanged());

        buyButton = addRenderableWidget(UiButton.create(
                Component.literal("BUY"),
                0,
                0,
                70,
                20,
                button -> requestTrade(true)
        ));

        sellButton = addRenderableWidget(UiButton.create(
                Component.literal("SELL"),
                0,
                0,
                70,
                20,
                button -> requestTrade(false)
        ));

        layoutActionWidgets();
    }

    private void recalcLayout() {
        frameWidth = Math.min(580, width - 20);
        frameHeight = Math.min(360, height - 36);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int innerX = frameX + 10;
        int innerY = frameY + 34;
        int innerWidth = frameWidth - 20;
        int innerHeight = frameHeight - 44;

        listX = innerX;
        listY = innerY;
        listWidth = clampInt((innerWidth * 38) / 100, 150, 230);
        listHeight = innerHeight;

        detailX = listX + listWidth + 8;
        detailY = innerY;
        detailWidth = innerWidth - listWidth - 8;
        detailHeight = clampInt((innerHeight * 62) / 100, 130, innerHeight - 90);

        actionX = detailX;
        actionY = detailY + detailHeight + 6;
        actionWidth = detailWidth;
        actionHeight = innerHeight - detailHeight - 6;
    }

    private void ensureListPanel() {
        int nextX = listX + 4;
        int nextY = listY + 18;
        int nextWidth = listWidth - 8;
        int nextHeight = listHeight - 22;

        if (stockListPanel != null
                && listPanelX == nextX
                && listPanelY == nextY
                && listPanelWidth == nextWidth
                && listPanelHeight == nextHeight) {
            return;
        }

        listPanelX = nextX;
        listPanelY = nextY;
        listPanelWidth = nextWidth;
        listPanelHeight = nextHeight;
        stockListPanel = new UiListPanel(nextX, nextY, nextWidth, nextHeight);
        refreshListEntries();
    }

    private void layoutActionWidgets() {
        if (quantityInput == null || buyButton == null || sellButton == null) {
            return;
        }

        int inputX = actionX + 10;
        int inputY = actionY + 20;
        int inputWidth = Math.max(70, actionWidth - 20);
        quantityInput.setPosition(inputX, inputY);
        quantityInput.setWidth(inputWidth);

        int buttonY = inputY + 24;
        int gap = 6;
        int buttonWidth = Math.max(52, (inputWidth - gap) / 2);
        buyButton.setPosition(inputX, buttonY);
        buyButton.setWidth(buttonWidth);
        sellButton.setPosition(inputX + buttonWidth + gap, buttonY);
        sellButton.setWidth(buttonWidth);
    }

    private void renderDetailPanel(GuiGraphics graphics) {
        int x = detailX + 8;
        int y = detailY + 22;
        int lineGap = 12;

        if (selectedStock == null) {
            graphics.drawString(font, Component.literal("Select a stock."), x, y, 0xC7D7F1, false);
            return;
        }

        graphics.drawString(font, Component.literal(selectedStock.name()), x, y, 0xFFFFFF, false);
        y += lineGap;
        graphics.drawString(font, Component.literal("Current: " + selectedStock.currentPrice()), x, y, 0xEAF1FF, false);
        y += lineGap;
        graphics.drawString(font, Component.literal("Holding: " + selectedStock.holdingQuantity()), x, y, 0xEAF1FF, false);
        y += lineGap;
        graphics.drawString(font, Component.literal("Avg Buy: " + selectedStock.avgBuyPrice()), x, y, 0xEAF1FF, false);
        y += lineGap;
        String pnlPrefix = selectedStock.unrealizedPnl() > 0 ? "+" : selectedStock.unrealizedPnl() < 0 ? "-" : "";
        int pnlAbs = Math.abs(selectedStock.unrealizedPnl());
        graphics.drawString(
                font,
                Component.literal("PnL: " + pnlPrefix + pnlAbs),
                x,
                y,
                selectedStock.unrealizedPnl() >= 0 ? 0x91F7A2 : 0xF7A4A4,
                false
        );
    }

    private void renderActionPanel(GuiGraphics graphics) {
        int x = actionX + 10;
        int y = actionY + 50;

        Integer quantity = validateQuantityInput();
        String buySellText = "Buy: - / Sell: -";
        String totalText = "Total: - (fee: -)";
        if (quantity != null && selectedStock != null) {
            int feeAmount = estimateFeeAmount(quantity);
            long buyTotal = estimateBuyTotal(quantity);
            long sellTotal = estimateSellTotal(quantity);
            buySellText = "Buy: " + buyTotal + " / Sell: " + sellTotal;
            totalText = "Total: " + calculateTotalPrice(quantity) + " (fee: " + feeAmount + ")";
        }

        graphics.drawString(font, Component.literal(buySellText), x, y, 0xEAF1FF, false);
        y += 12;
        graphics.drawString(font, Component.literal(totalText), x, y, 0xEAF1FF, false);
        y += 12;
        graphics.drawString(font, Component.literal("Wallet: " + walletBalance), x, y, 0xDDE6F9, false);

        if (inputError != null && !inputError.isBlank()) {
            y += 14;
            graphics.drawString(font, Component.literal(inputError), x, y, 0xFF8E8E, false);
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            y += 14;
            graphics.drawString(font, Component.literal(statusMessage), x, y, 0xDDE6F9, false);
        }
    }

    private void requestStockList(boolean forceRefresh) {
        listLoading = true;
        detailReady = false;
        setLoading(true, Component.literal("Loading stocks..."));
        setError(null);
        pendingListRequestId = forceRefresh
                ? UiClientNetworking.requestInvestRefresh()
                : UiClientNetworking.requestInvestList(false);
        updateActionButtons();
    }

    private void requestStockDetail(String stockId, boolean forceRefresh) {
        detailLoading = true;
        detailReady = false;
        pendingDetailRequestId = UiClientNetworking.requestInvestDetail(stockId, forceRefresh);
        updateActionButtons();
    }

    private void requestTrade(boolean buy) {
        if (selectedStock == null || tradeLoading || listLoading || detailLoading || !detailReady) {
            return;
        }

        Integer quantity = validateQuantityInput();
        if (quantity == null) {
            return;
        }

        if (buy && !canBuy(quantity)) {
            return;
        }
        if (!buy && !canSell(quantity)) {
            return;
        }

        tradeLoading = true;
        inputError = null;
        statusMessage = null;
        if (buy) {
            pendingBuyRequestId = UiClientNetworking.requestInvestBuy(selectedStock.stockId(), quantity);
        } else {
            pendingSellRequestId = UiClientNetworking.requestInvestSell(selectedStock.stockId(), quantity);
        }
        updateActionButtons();
    }

    private void handleListResponse(UiResponsePayload payload) {
        if (pendingListRequestId != null && !pendingListRequestId.equals(payload.requestId())) {
            return;
        }
        pendingListRequestId = null;
        listLoading = false;
        setLoading(false);

        if (!payload.success()) {
            setError("Failed to load stocks.");
            stocks.clear();
            selectedStock = null;
            detailReady = false;
            refreshListEntries();
            updateActionButtons();
            return;
        }

        try {
            String previousSelectedId = selectedStock == null ? null : selectedStock.stockId();
            List<InvestStockViewData> parsed = InvestStockJsonParser.parseStockList(payload.dataJson());
            stocks.clear();
            stocks.addAll(parsed);
            setError(null);
            refreshListEntries();
            restoreSelection(previousSelectedId);
        } catch (Exception ex) {
            setError("Failed to load stocks.");
            stocks.clear();
            selectedStock = null;
            detailReady = false;
            refreshListEntries();
        }

        updateActionButtons();
    }

    private void handleDetailResponse(UiResponsePayload payload) {
        if (pendingDetailRequestId != null && !pendingDetailRequestId.equals(payload.requestId())) {
            return;
        }
        pendingDetailRequestId = null;
        detailLoading = false;

        if (!payload.success()) {
            detailReady = false;
            setError("Failed to load stock.");
            updateActionButtons();
            return;
        }

        try {
            InvestStockDetailViewData detail = InvestStockJsonParser.parseStockDetail(payload.dataJson());
            walletBalance = detail.walletBalance();
            upsertStock(detail.stock());
            selectedStock = detail.stock();
            detailReady = true;
            setError(null);
            refreshListEntries();
            updateSelectionByStockId(selectedStock.stockId());
        } catch (Exception ex) {
            detailReady = false;
            setError("Failed to load stock.");
        }

        updateActionButtons();
    }

    private void handleTradeResponse(UiResponsePayload payload) {
        boolean isBuyLike = payload.action() == UiAction.INVEST_BUY || payload.action() == UiAction.INVEST_CONTRIBUTE;
        String expectedRequestId = isBuyLike ? pendingBuyRequestId : pendingSellRequestId;
        if (expectedRequestId != null && !expectedRequestId.equals(payload.requestId())) {
            return;
        }

        if (isBuyLike) {
            pendingBuyRequestId = null;
        } else {
            pendingSellRequestId = null;
        }
        tradeLoading = false;

        if (!payload.success()) {
            String tradeError = resolveTradeErrorMessage(isBuyLike, payload.error());
            setError(tradeError);
            statusMessage = tradeError;
            updateActionButtons();
            return;
        }

        try {
            InvestTradeResultViewData result = InvestStockJsonParser.parseTradeResult(payload.dataJson());
            walletBalance = result.walletBalanceAfter();
            if (result.stock() != null) {
                upsertStock(result.stock());
                selectedStock = result.stock();
                detailReady = true;
                updateSelectionByStockId(result.stock().stockId());
            }

            String sideMessage = "sell".equalsIgnoreCase(result.side())
                    ? "Sold " + result.quantity() + " shares"
                    : "Bought " + result.quantity() + " shares";
            statusMessage = sideMessage;
            setError(null);

            requestStockList(true);
            if (selectedStock != null) {
                requestStockDetail(selectedStock.stockId(), true);
            }
        } catch (Exception ex) {
            String tradeError = resolveTradeErrorMessage(isBuyLike, null);
            setError(tradeError);
            statusMessage = tradeError;
        }

        updateActionButtons();
    }

    private void onSelectedStockChanged() {
        if (stockListPanel == null || stocks.isEmpty()) {
            selectedStock = null;
            detailReady = false;
            updateActionButtons();
            return;
        }
        int selectedIndex = Math.max(0, Math.min(stockListPanel.selectedIndex(), stocks.size() - 1));
        selectedStock = stocks.get(selectedIndex);
        statusMessage = null;
        requestStockDetail(selectedStock.stockId(), false);
        updateActionButtons();
    }

    private void restoreSelection(@Nullable String stockId) {
        if (stockListPanel == null || stocks.isEmpty()) {
            selectedStock = null;
            detailReady = false;
            setEmpty(Component.literal("No stocks available."));
            return;
        }

        setEmpty(null);
        int selectedIndex = 0;
        if (stockId != null) {
            for (int i = 0; i < stocks.size(); i++) {
                if (stockId.equals(stocks.get(i).stockId())) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        stockListPanel.setSelectedIndex(selectedIndex);
        selectedStock = stocks.get(selectedIndex);
        requestStockDetail(selectedStock.stockId(), false);
    }

    private void updateSelectionByStockId(String stockId) {
        if (stockListPanel == null || stockId == null) {
            return;
        }
        for (int i = 0; i < stocks.size(); i++) {
            if (stockId.equals(stocks.get(i).stockId())) {
                stockListPanel.setSelectedIndex(i);
                return;
            }
        }
    }

    private void refreshListEntries() {
        if (stockListPanel == null) {
            return;
        }
        List<Component> entries = new ArrayList<>();
        for (InvestStockViewData stock : stocks) {
            entries.add(Component.literal(stock.listLabel()));
        }
        stockListPanel.setEntries(entries);
    }

    private void upsertStock(InvestStockViewData updated) {
        for (int i = 0; i < stocks.size(); i++) {
            if (stocks.get(i).stockId().equals(updated.stockId())) {
                stocks.set(i, updated);
                return;
            }
        }
        stocks.add(updated);
    }

    private void onQuantityChanged() {
        validateQuantityInput();
        statusMessage = null;
        updateActionButtons();
    }

    @Nullable
    private Integer validateQuantityInput() {
        if (quantityInput == null) {
            inputError = null;
            return null;
        }

        String raw = quantityInput.getValue();
        if (raw == null || raw.isBlank()) {
            inputError = null;
            return null;
        }

        try {
            int quantity = Integer.parseInt(raw);
            if (quantity <= 0) {
                inputError = "Quantity must be greater than 0.";
                return null;
            }
            inputError = null;
            return quantity;
        } catch (NumberFormatException ex) {
            inputError = "Quantity must be numeric.";
            return null;
        }
    }

    private long calculateTotalPrice(@Nullable Integer quantity) {
        if (quantity == null || selectedStock == null) {
            return 0L;
        }
        return (long) quantity * Math.max(0, selectedStock.currentPrice());
    }

    private int estimateFeeAmount(@Nullable Integer quantity) {
        return quantity == null || quantity <= 0 ? 0 : 0;
    }

    private long estimateBuyTotal(int quantity) {
        return calculateTotalPrice(quantity) + estimateFeeAmount(quantity);
    }

    private long estimateSellTotal(int quantity) {
        return Math.max(0L, calculateTotalPrice(quantity) - estimateFeeAmount(quantity));
    }

    private String resolveTradeErrorMessage(boolean isBuyLike, @Nullable String rawError) {
        String normalized = rawError == null ? "" : rawError.toLowerCase();
        if (normalized.contains("balance")) {
            return "Not enough balance";
        }
        if (normalized.contains("quantity") || normalized.contains("share") || normalized.contains("holding")) {
            return "Not enough shares";
        }
        return isBuyLike ? "Not enough balance" : "Not enough shares";
    }

    private boolean canBuy(int quantity) {
        if (selectedStock == null || quantity <= 0 || !detailReady) {
            return false;
        }
        int price = selectedStock.currentPrice();
        if (price <= 0) {
            return false;
        }
        long totalPrice = estimateBuyTotal(quantity);
        return totalPrice <= walletBalance;
    }

    private boolean canSell(int quantity) {
        if (selectedStock == null || quantity <= 0 || !detailReady) {
            return false;
        }
        return selectedStock.holdingQuantity() >= quantity;
    }

    private void updateActionButtons() {
        boolean busy = listLoading || detailLoading || tradeLoading;
        Integer quantity = validateQuantityInput();
        boolean buyEnabled = !busy && quantity != null && canBuy(quantity);
        boolean sellEnabled = !busy && quantity != null && canSell(quantity);

        if (buyButton != null) {
            buyButton.active = buyEnabled;
        }
        if (sellButton != null) {
            sellButton.active = sellEnabled;
        }
        if (refreshButton != null) {
            refreshButton.active = !busy;
        }
        if (closeButton != null) {
            closeButton.active = !tradeLoading;
        }
    }
}
