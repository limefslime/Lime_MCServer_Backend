# UI Text Overflow / Alignment Stabilization - Full Changed Code

## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/widget/UiTextRender.java

```java
package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public final class UiTextRender {
    private static final String ELLIPSIS = "...";

    private UiTextRender() {}

    public static String ellipsize(Font font, @Nullable String value, int maxWidth) {
        if (value == null || value.isBlank() || maxWidth <= 0) {
            return "";
        }
        if (font.width(value) <= maxWidth) {
            return value;
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        String candidate = font.plainSubstrByWidth(value, maxWidth - ellipsisWidth);
        while (!candidate.isEmpty() && font.width(candidate + ELLIPSIS) > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isEmpty()) {
            return "";
        }
        return candidate + ELLIPSIS;
    }

    public static void drawEllipsized(
            GuiGraphics graphics,
            Font font,
            @Nullable String value,
            int x,
            int y,
            int maxWidth,
            int color
    ) {
        String text = ellipsize(font, value, maxWidth);
        if (text.isEmpty()) {
            return;
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    public static void drawRightAligned(
            GuiGraphics graphics,
            Font font,
            @Nullable String value,
            int rightX,
            int y,
            int maxWidth,
            int color
    ) {
        String text = ellipsize(font, value, maxWidth);
        if (text.isEmpty()) {
            return;
        }
        int drawX = rightX - font.width(text);
        graphics.drawString(font, text, drawX, y, color, false);
    }

    public static void drawLabelValue(
            GuiGraphics graphics,
            Font font,
            @Nullable String label,
            @Nullable String value,
            int x,
            int y,
            int totalWidth,
            int labelWidth,
            int labelColor,
            int valueColor
    ) {
        if (totalWidth <= 0) {
            return;
        }
        int safeLabelWidth = Math.max(16, Math.min(labelWidth, Math.max(16, totalWidth - 10)));
        int valueWidth = Math.max(0, totalWidth - safeLabelWidth - 6);
        drawEllipsized(graphics, font, label, x, y, safeLabelWidth, labelColor);
        drawRightAligned(graphics, font, value, x + totalWidth, y, valueWidth, valueColor);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/widget/UiButton.java

```java
package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class UiButton {
    private UiButton() {}

    public static Button create(Component label, int x, int y, int width, int height, Button.OnPress onPress) {
        return new FittedLabelButton(x, y, width, height, label, onPress);
    }

    private static final class FittedLabelButton extends Button {
        private Component fullLabel;

        private FittedLabelButton(int x, int y, int width, int height, Component label, OnPress onPress) {
            super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
            this.fullLabel = label == null ? Component.empty() : label;
        }

        @Override
        public void setMessage(Component message) {
            super.setMessage(message);
            this.fullLabel = message == null ? Component.empty() : message;
        }

        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            int maxWidth = Math.max(8, getWidth() - 10);
            String fitted = UiTextRender.ellipsize(font, fullLabel == null ? "" : fullLabel.getString(), maxWidth);
            if (fitted.isEmpty()) {
                return;
            }
            int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
            graphics.drawCenteredString(font, fitted, getX() + (getWidth() / 2), textY, color);
        }
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/widget/UiListPanel.java

```java
package com.namanseul.farmingmod.client.ui.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class UiListPanel {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<Component> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    @Nullable
    private RowRenderer rowRenderer;

    public UiListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 14);
    }

    public UiListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<Component> newEntries) {
        this.entries = new ArrayList<>(newEntries == null ? Collections.emptyList() : newEntries);
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (selectedIndex >= entries.size()) {
            selectedIndex = entries.size() - 1;
        }
        clampScroll();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        clampScroll();
    }

    public Component selectedEntry() {
        if (entries.isEmpty()) {
            return Component.empty();
        }
        return entries.get(Math.max(0, Math.min(selectedIndex, entries.size() - 1)));
    }

    public void setRowRenderer(@Nullable RowRenderer renderer) {
        this.rowRenderer = renderer;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x99202633);
        graphics.fill(x, y, x + width, y + 1, 0xFF4D5E7D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4D5E7D);
        graphics.fill(x, y, x + 1, y + height, 0xFF4D5E7D);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF4D5E7D);

        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int baseY = y + 4 - scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight < y || rowY > y + height) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY - 1, x + width - 2, rowY + rowHeight - 2, 0xAA3A4A6A);
            }
            int rowTextX = x + 6;
            int rowTextY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            int rowTextWidth = Math.max(0, width - 12);
            if (rowRenderer != null) {
                rowRenderer.render(
                        graphics,
                        font,
                        i,
                        entries.get(i),
                        rowTextX,
                        rowTextY,
                        rowTextWidth,
                        rowHeight,
                        i == selectedIndex
                );
                continue;
            }
            if (!renderStructuredEntry(graphics, font, entries.get(i).getString(), rowTextX, rowTextY, rowTextWidth)) {
                UiTextRender.drawEllipsized(graphics, font, entries.get(i).getString(), rowTextX, rowTextY, rowTextWidth, 0xFFFFFF);
            }
        }
        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }

        int relativeY = (int) mouseY - y - 4 + scrollOffset;
        int clickedIndex = relativeY / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < entries.size()) {
            selectedIndex = clickedIndex;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int step = Math.max(1, rowHeight - 2);
        scrollOffset -= (int) Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int maxOffset = Math.max(0, entries.size() * rowHeight - (height - 8));
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean renderStructuredEntry(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String[] pipeColumns = Arrays.stream(text.split("\\|"))
                .map(String::trim)
                .toArray(String[]::new);
        if (pipeColumns.length == 2) {
            int rightWidth = Math.max(48, Math.min(120, width / 3));
            int leftWidth = Math.max(24, width - rightWidth - 8);
            UiTextRender.drawEllipsized(graphics, font, pipeColumns[0], x, y, leftWidth, 0xFFFFFF);
            UiTextRender.drawRightAligned(graphics, font, pipeColumns[1], x + width, y, rightWidth, 0xE8F0FF);
            return true;
        }
        if (pipeColumns.length >= 3) {
            String left = pipeColumns[0];
            String right = pipeColumns[pipeColumns.length - 1];
            String middle = String.join(" | ", Arrays.asList(pipeColumns).subList(1, pipeColumns.length - 1));

            int leftWidth = Math.max(36, Math.min(64, width / 4));
            int rightWidth = Math.max(40, Math.min(88, width / 4));
            int middleX = x + leftWidth + 6;
            int middleWidth = Math.max(24, width - leftWidth - rightWidth - 12);

            UiTextRender.drawEllipsized(graphics, font, left, x, y, leftWidth, 0xBFD0E8);
            UiTextRender.drawEllipsized(graphics, font, middle, middleX, y, middleWidth, 0xFFFFFF);
            UiTextRender.drawRightAligned(graphics, font, right, x + width, y, rightWidth, 0xE8F0FF);
            return true;
        }

        int colonIndex = text.indexOf(':');
        if (colonIndex > 0 && colonIndex < text.length() - 1) {
            String label = text.substring(0, colonIndex + 1).trim();
            String value = text.substring(colonIndex + 1).trim();
            if (label.length() <= 24 && !value.isBlank()) {
                int labelWidth = Math.max(40, Math.min(112, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return true;
            }
        }

        return false;
    }

    @FunctionalInterface
    public interface RowRenderer {
        void render(
                GuiGraphics graphics,
                Font font,
                int rowIndex,
                Component entry,
                int rowX,
                int rowY,
                int rowWidth,
                int rowHeight,
                boolean selected
        );
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/widget/UiMessageBanner.java

```java
package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public record UiMessageBanner(MessageType type, Component message) {
    public enum MessageType {
        INFO(0xAA224422, 0xFF66CC88),
        WARNING(0xAA4C3D19, 0xFFF0C060),
        ERROR(0xAA4A1A1A, 0xFFFF6B6B);

        private final int fillColor;
        private final int borderColor;

        MessageType(int fillColor, int borderColor) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
        }
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, type.fillColor);
        graphics.fill(x, y, x + width, y + 1, type.borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, type.borderColor);
        graphics.fill(x, y, x + 1, y + height, type.borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, type.borderColor);
        int textX = x + 6;
        int textY = y + Math.max(0, (height - font.lineHeight) / 2);
        int textWidth = Math.max(0, width - 12);
        UiTextRender.drawEllipsized(graphics, font, message == null ? "" : message.getString(), textX, textY, textWidth, 0xFFFFFF);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/screen/InvestScreen.java

```java
package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.invest.InvestStockDetailViewData;
import com.namanseul.farmingmod.client.ui.invest.InvestStockJsonParser;
import com.namanseul.farmingmod.client.ui.invest.InvestStockViewData;
import com.namanseul.farmingmod.client.ui.invest.InvestTradeResultViewData;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
    private static final int DETAIL_LABEL_WIDTH = 72;
    private static final int ACTION_LABEL_WIDTH = 58;

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
        stockListPanel.setRowRenderer(this::renderStockListRow);
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
        int contentX = detailX + 8;
        int contentWidth = Math.max(0, detailWidth - 16);
        int y = detailY + 22;

        if (selectedStock == null) {
            UiTextRender.drawEllipsized(graphics, font, "Select a stock.", contentX, y, contentWidth, 0xC7D7F1);
            return;
        }

        UiTextRender.drawEllipsized(graphics, font, selectedStock.name(), contentX, y, contentWidth, 0xFFFFFF);
        int dividerY = y + font.lineHeight + 2;
        graphics.fill(contentX, dividerY, contentX + contentWidth, dividerY + 1, 0xAA5F769A);

        y += 18;
        UiTextRender.drawLabelValue(
                graphics,
                font,
                "Current:",
                formatAmount(selectedStock.currentPrice()),
                contentX,
                y,
                contentWidth,
                DETAIL_LABEL_WIDTH,
                0xC7D7F1,
                0xEAF1FF
        );
        y += 13;
        UiTextRender.drawLabelValue(
                graphics,
                font,
                "Holding:",
                formatAmount(selectedStock.holdingQuantity()),
                contentX,
                y,
                contentWidth,
                DETAIL_LABEL_WIDTH,
                0xC7D7F1,
                0xEAF1FF
        );
        y += 13;
        UiTextRender.drawLabelValue(
                graphics,
                font,
                "Avg Buy:",
                formatAmount(selectedStock.avgBuyPrice()),
                contentX,
                y,
                contentWidth,
                DETAIL_LABEL_WIDTH,
                0xC7D7F1,
                0xEAF1FF
        );
        y += 17;

        int pnlValue = selectedStock.unrealizedPnl();
        String pnlText = (pnlValue >= 0 ? "+" : "-") + formatAmount(Math.abs(pnlValue));
        int pnlColor = pnlValue > 0 ? 0x91F7A2 : pnlValue < 0 ? 0xF7A4A4 : 0xEAF1FF;
        UiTextRender.drawLabelValue(
                graphics,
                font,
                "PnL:",
                pnlText,
                contentX,
                y,
                contentWidth,
                DETAIL_LABEL_WIDTH,
                0xC7D7F1,
                pnlColor
        );
    }

    private void renderActionPanel(GuiGraphics graphics) {
        int contentX = actionX + 10;
        int contentWidth = Math.max(0, actionWidth - 20);
        int y = actionY + 50;

        Integer quantity = validateQuantityInput();
        String buyValue = "-";
        String sellValue = "-";
        String totalValue = "-";
        String feeValue = null;
        if (quantity != null && selectedStock != null) {
            int feeAmount = estimateFeeAmount(quantity);
            long buyTotal = estimateBuyTotal(quantity);
            long sellTotal = estimateSellTotal(quantity);
            buyValue = formatAmount(buyTotal);
            sellValue = formatAmount(sellTotal);
            totalValue = formatAmount(calculateTotalPrice(quantity));
            if (feeAmount > 0) {
                feeValue = formatAmount(feeAmount);
            }
        }

        UiTextRender.drawLabelValue(graphics, font, "Buy:", buyValue, contentX, y, contentWidth, ACTION_LABEL_WIDTH, 0xC7D7F1, 0xEAF1FF);
        y += 12;
        UiTextRender.drawLabelValue(graphics, font, "Sell:", sellValue, contentX, y, contentWidth, ACTION_LABEL_WIDTH, 0xC7D7F1, 0xEAF1FF);
        y += 16;
        UiTextRender.drawLabelValue(graphics, font, "Total:", totalValue, contentX, y, contentWidth, ACTION_LABEL_WIDTH, 0xC7D7F1, 0xEAF1FF);
        if (feeValue != null) {
            y += 12;
            UiTextRender.drawLabelValue(graphics, font, "Fee:", feeValue, contentX, y, contentWidth, ACTION_LABEL_WIDTH, 0xC7D7F1, 0xEAF1FF);
        }
        y += 12;
        UiTextRender.drawLabelValue(
                graphics,
                font,
                "Wallet:",
                formatAmount(walletBalance),
                contentX,
                y,
                contentWidth,
                ACTION_LABEL_WIDTH,
                0xC7D7F1,
                0xDDE6F9
        );

        if (inputError != null && !inputError.isBlank()) {
            y += 14;
            UiTextRender.drawEllipsized(graphics, font, inputError, contentX, y, contentWidth, 0xFF8E8E);
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            y += 14;
            UiTextRender.drawEllipsized(graphics, font, statusMessage, contentX, y, contentWidth, 0xDDE6F9);
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
            String errorMessage = payload.error() != null && !payload.error().isBlank()
                    ? payload.error()
                    : "Failed to load stocks.";
            setError(errorMessage);
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
            String errorMessage = payload.error() != null && !payload.error().isBlank()
                    ? payload.error()
                    : "Failed to load stock.";
            setError(errorMessage);
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
            entries.add(Component.literal(stock.stockId() == null ? "" : stock.stockId()));
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
        if (rawError != null && !rawError.isBlank()) {
            return rawError;
        }
        String normalized = rawError == null ? "" : rawError.toLowerCase();
        if (normalized.contains("balance")) {
            return "Not enough balance";
        }
        if (normalized.contains("quantity") || normalized.contains("share") || normalized.contains("holding")) {
            return "Not enough shares";
        }
        return isBuyLike ? "Not enough balance" : "Not enough shares";
    }

    private String formatAmount(long value) {
        return String.format("%,d", value);
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

    private void renderStockListRow(
            GuiGraphics graphics,
            net.minecraft.client.gui.Font listFont,
            int rowIndex,
            Component entry,
            int rowX,
            int rowY,
            int rowWidth,
            int rowHeight,
            boolean selected
    ) {
        if (rowIndex < 0 || rowIndex >= stocks.size()) {
            UiTextRender.drawEllipsized(graphics, listFont, entry.getString(), rowX, rowY, rowWidth, 0xFFFFFF);
            return;
        }

        InvestStockViewData stock = stocks.get(rowIndex);
        int gap = 6;
        int changeWidth = Math.max(44, Math.min(84, rowWidth / 4));
        int priceWidth = Math.max(54, Math.min(96, rowWidth / 3));
        int nameWidth = Math.max(28, rowWidth - changeWidth - priceWidth - gap * 2);

        int nameX = rowX;
        int priceRight = rowX + nameWidth + gap + priceWidth;
        int changeRight = rowX + rowWidth;

        String priceText = formatAmount(stock.currentPrice());
        String changeText;
        if (stock.changeAmount() > 0) {
            changeText = "+" + formatAmount(stock.changeAmount());
        } else if (stock.changeAmount() < 0) {
            changeText = "-" + formatAmount(Math.abs(stock.changeAmount()));
        } else {
            changeText = "0";
        }
        int changeColor = stock.changeAmount() > 0 ? 0x91F7A2 : stock.changeAmount() < 0 ? 0xF7A4A4 : 0xC7D7F1;

        UiTextRender.drawEllipsized(graphics, listFont, stock.name(), nameX, rowY, nameWidth, 0xFFFFFF);
        UiTextRender.drawRightAligned(graphics, listFont, priceText, priceRight, rowY, priceWidth, 0xE8F0FF);
        UiTextRender.drawRightAligned(graphics, listFont, changeText, changeRight, rowY, changeWidth, changeColor);
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
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/screen/ShopScreen.java

```java
package com.namanseul.farmingmod.client.ui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.shop.ShopActionPanelView;
import com.namanseul.farmingmod.client.ui.shop.ShopDetailPanelView;
import com.namanseul.farmingmod.client.ui.shop.ShopItemViewData;
import com.namanseul.farmingmod.client.ui.shop.ShopJsonParser;
import com.namanseul.farmingmod.client.ui.shop.ShopItemListPanel;
import com.namanseul.farmingmod.client.ui.shop.ShopPreviewViewData;
import com.namanseul.farmingmod.client.ui.shop.ShopTradeViewData;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ShopScreen extends BaseGameScreen {
    private static final String MSG_SELECT_ITEM = "Select an item first.";
    private static final String MSG_INVALID_QUANTITY = "Enter a quantity of 1 or more.";
    private static final String MSG_PREVIEW_PENDING = "Price check is in progress.";
    private static final String MSG_SELECT_LISTED_ITEM = "Select your listed item first.";
    private static final String MSG_INVALID_INVENTORY_ITEM = "Could not read the selected inventory item.";

    private final Screen returnScreen;
    private final List<ShopItemViewData> items = new ArrayList<>();
    private final List<InventoryChoice> inventoryChoices = new ArrayList<>();

    private ShopItemListPanel itemListPanel;
    private ShopItemViewData selectedItem;
    private ShopPreviewViewData buyPreview;
    private ShopPreviewViewData sellPreview;
    private ShopTradeViewData lastTrade;

    private EditBox quantityInput;
    private Button buyButton;
    private Button sellButton;
    private Button cancelSellButton;
    private Button registerItemButton;
    private Button quantityOneButton;
    private Button quantityTenButton;
    private Button quantityStackButton;
    private UiListPanel inventoryListPanel;
    private Button inventorySelectButton;
    private Button inventoryCancelButton;
    private boolean inventoryPickerVisible;

    private String pendingListRequestId;
    private String pendingDetailRequestId;
    private String pendingPreviewBuyRequestId;
    private String pendingPreviewSellRequestId;
    private String pendingTradeRequestId;
    private String pendingRegisterRequestId;
    private String pendingCancelSellRequestId;

    private boolean listLoading;
    private boolean previewLoading;
    private boolean tradeLoading;
    private boolean listingActionLoading;

    private String quantityError;
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
    private int inventoryPickerX;
    private int inventoryPickerY;
    private int inventoryPickerWidth;
    private int inventoryPickerHeight;

    public ShopScreen(@Nullable Screen returnScreen) {
        super(Component.translatable("screen.namanseulfarming.shop.title"));
        this.returnScreen = returnScreen;
    }

    public static ShopScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ShopScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        ShopScreen screen = new ShopScreen(parent);
        minecraft.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();

        initCommonButtons(frameX + frameWidth - 4, frameY + 8);
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.shop.back"));
        }

        itemListPanel = new ShopItemListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);
        initActionWidgets();
        initInventoryPickerWidgets();
        requestItemList(false);
    }

    @Override
    protected void onRefreshPressed() {
        requestItemList(true);
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
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        layoutActionWidgets();
        updateActionButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.shop.items"), listX + 6, listY + 6);
        if (itemListPanel != null) {
            itemListPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.literal("Trade"), detailX + 6, detailY + 6);
        renderClipped(graphics, detailX, detailY, detailWidth, detailHeight, () ->
                ShopDetailPanelView.render(
                        graphics,
                        font,
                        detailX + 2,
                        detailY + 18,
                        detailWidth - 4,
                        detailHeight - 20,
                        selectedItem,
                        buyPreview,
                        sellPreview,
                        previewLoading,
                        lastTrade
                )
        );

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.shop.actions"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () ->
                ShopActionPanelView.render(
                        graphics,
                        font,
                        actionX + 8,
                        actionY + 20,
                        actionWidth - 16,
                        quantityError,
                        statusMessage,
                        previewLoading,
                        tradeLoading
                )
        );

        if (inventoryPickerVisible) {
            renderInventoryPicker(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inventoryPickerVisible) {
            if (inventoryListPanel != null && inventoryListPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (itemListPanel != null && itemListPanel.mouseClicked(mouseX, mouseY, button)) {
            onSelectedIndexChanged();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inventoryPickerVisible) {
            if (inventoryListPanel != null && inventoryListPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        if (itemListPanel != null && itemListPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void handleServerResponse(UiResponsePayload payload) {
        switch (payload.action()) {
            case SHOP_LIST -> handleListResponse(payload);
            case SHOP_DETAIL -> handleDetailResponse(payload);
            case SHOP_PREVIEW_BUY, SHOP_PREVIEW_SELL -> handlePreviewResponse(payload);
            case SHOP_BUY, SHOP_SELL -> handleTradeResponse(payload);
            case SHOP_REGISTER, SHOP_CANCEL_SELL -> handleListingActionResponse(payload);
            default -> {
                // ignore unrelated actions
            }
        }
    }

    private void initActionWidgets() {
        quantityInput = addRenderableWidget(new EditBox(
                font,
                0,
                0,
                48,
                16,
                Component.translatable("screen.namanseulfarming.shop.quantity")
        ));
        quantityInput.setMaxLength(5);
        quantityInput.setFilter(value -> value.isEmpty() || value.matches("\\d{0,5}"));
        quantityInput.setValue("1");
        quantityInput.setResponder(value -> onQuantityInputChanged());

        quantityOneButton = addRenderableWidget(UiButton.create(
                Component.literal("1"),
                0,
                0,
                24,
                20,
                button -> setQuickQuantity(1)
        ));
        quantityTenButton = addRenderableWidget(UiButton.create(
                Component.literal("10"),
                0,
                0,
                24,
                20,
                button -> setQuickQuantity(10)
        ));
        quantityStackButton = addRenderableWidget(UiButton.create(
                Component.literal("64"),
                0,
                0,
                24,
                20,
                button -> setQuickQuantity(64)
        ));

        registerItemButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.register_item"),
                0,
                0,
                64,
                20,
                button -> openInventoryPicker()
        ));
        buyButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.buy"),
                0,
                0,
                64,
                20,
                button -> requestTrade(true)
        ));
        sellButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.sell"),
                0,
                0,
                64,
                20,
                button -> requestTrade(false)
        ));
        cancelSellButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.cancel_sell"),
                0,
                0,
                64,
                20,
                button -> requestCancelSell()
        ));

        layoutActionWidgets();
    }

    private void initInventoryPickerWidgets() {
        inventoryListPanel = new UiListPanel(
                inventoryPickerX + 8,
                inventoryPickerY + 24,
                inventoryPickerWidth - 16,
                inventoryPickerHeight - 54
        );

        inventorySelectButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.inventory_select"),
                inventoryPickerX + 8,
                inventoryPickerY + inventoryPickerHeight - 24,
                72,
                20,
                button -> applySelectedInventoryItem()
        ));
        inventoryCancelButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.shop.inventory_cancel"),
                inventoryPickerX + inventoryPickerWidth - 80,
                inventoryPickerY + inventoryPickerHeight - 24,
                72,
                20,
                button -> closeInventoryPicker()
        ));
        setInventoryPickerWidgetsVisible(false);
    }

    private void renderInventoryPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xFF070A12);
        graphics.fill(
                inventoryPickerX,
                inventoryPickerY,
                inventoryPickerX + inventoryPickerWidth,
                inventoryPickerY + inventoryPickerHeight,
                0xFF121A2B
        );
        graphics.fill(inventoryPickerX, inventoryPickerY, inventoryPickerX + inventoryPickerWidth, inventoryPickerY + 1, 0xFF6E86B0);
        graphics.fill(inventoryPickerX, inventoryPickerY + inventoryPickerHeight - 1, inventoryPickerX + inventoryPickerWidth, inventoryPickerY + inventoryPickerHeight, 0xFF6E86B0);
        graphics.fill(inventoryPickerX, inventoryPickerY, inventoryPickerX + 1, inventoryPickerY + inventoryPickerHeight, 0xFF6E86B0);
        graphics.fill(inventoryPickerX + inventoryPickerWidth - 1, inventoryPickerY, inventoryPickerX + inventoryPickerWidth, inventoryPickerY + inventoryPickerHeight, 0xFF6E86B0);
        renderSectionTitle(
                graphics,
                Component.translatable("screen.namanseulfarming.shop.inventory_picker_title"),
                inventoryPickerX + 8,
                inventoryPickerY + 8
        );
        if (inventoryListPanel != null) {
            inventoryListPanel.render(graphics, font, mouseX, mouseY);
        }
    }

    private void requestItemList(boolean forceRefresh) {
        listLoading = true;
        closeInventoryPicker();
        setLoading(true, Component.translatable("screen.namanseulfarming.shop.loading_items"));
        setError(null);
        statusMessage = null;
        pendingListRequestId = UiClientNetworking.requestShopList(forceRefresh);
        updateActionButtons();
    }

    private void requestItemDetail(String itemId, boolean forceRefresh) {
        pendingDetailRequestId = UiClientNetworking.requestShopDetail(itemId, forceRefresh);
    }

    private void requestAutoPreviews() {
        if (selectedItem == null) {
            clearPreviews();
            updateActionButtons();
            return;
        }

        Integer quantity = validatedQuantity();
        if (quantity == null) {
            clearPreviews();
            updateActionButtons();
            return;
        }

        setError(null);
        statusMessage = null;
        pendingPreviewBuyRequestId = UiClientNetworking.requestShopPreviewBuy(selectedItem.itemId(), quantity);
        pendingPreviewSellRequestId = UiClientNetworking.requestShopPreviewSell(selectedItem.itemId(), quantity);
        previewLoading = true;
        updateActionButtons();
    }

    private void requestTrade(boolean buy) {
        if (selectedItem == null) {
            showActionHint(MSG_SELECT_ITEM);
            return;
        }

        Integer quantity = validatedQuantity();
        if (quantity == null) {
            showActionHint(MSG_INVALID_QUANTITY);
            return;
        }

        if (!hasMatchingPreview(buy ? "buy" : "sell", quantity)) {
            showActionHint(MSG_PREVIEW_PENDING);
            return;
        }

        tradeLoading = true;
        statusMessage = null;
        setError(null);
        pendingTradeRequestId = buy
                ? UiClientNetworking.requestShopBuy(selectedItem.itemId(), quantity)
                : UiClientNetworking.requestShopSell(selectedItem.itemId(), quantity);
        updateActionButtons();
    }

    private void requestRegisterItem(InventoryChoice choice) {
        if (choice == null || choice.itemKey() == null || choice.itemKey().isBlank()) {
            showActionHint(MSG_INVALID_INVENTORY_ITEM);
            return;
        }
        Integer quantity = validatedQuantity();
        if (quantity == null) {
            showActionHint(MSG_INVALID_QUANTITY);
            return;
        }
        if (listingActionLoading) {
            return;
        }

        listingActionLoading = true;
        setError(null);
        statusMessage = "Registering item...";
        pendingRegisterRequestId = UiClientNetworking.requestShopRegister(
                choice.itemKey(),
                choice.stack().getDisplayName().getString(),
                quantity,
                choice.slot()
        );
        updateActionButtons();
    }

    private void requestCancelSell() {
        if (selectedItem == null || !selectedItem.playerListed()) {
            showActionHint(MSG_SELECT_LISTED_ITEM);
            return;
        }
        if (listingActionLoading) {
            return;
        }

        listingActionLoading = true;
        setError(null);
        statusMessage = "Canceling listing...";
        pendingCancelSellRequestId = UiClientNetworking.requestShopCancelSell(selectedItem.itemId());
        updateActionButtons();
    }

    private void onSelectedIndexChanged() {
        if (itemListPanel == null || items.isEmpty()) {
            selectedItem = null;
            clearPreviews();
            return;
        }
        int selectedIndex = Math.max(0, Math.min(itemListPanel.selectedIndex(), items.size() - 1));
        selectedItem = items.get(selectedIndex);
        clearPreviews();
        quantityError = null;
        statusMessage = null;
        requestItemDetail(selectedItem.itemId(), false);
        requestAutoPreviews();
        updateActionButtons();
    }

    private void onQuantityInputChanged() {
        validatedQuantity();
        clearPreviews();
        requestAutoPreviews();
        updateActionButtons();
    }

    private void setQuickQuantity(int value) {
        if (quantityInput == null) {
            return;
        }
        quantityInput.setValue(Integer.toString(value));
        onQuantityInputChanged();
    }

    private void openInventoryPicker() {
        if (inventoryListPanel == null) {
            return;
        }

        if (quantityInput != null) {
            quantityInput.setFocused(false);
        }

        loadInventoryChoices();
        setInventoryPickerWidgetsVisible(true);
        setMainWidgetsVisible(false);
        inventoryPickerVisible = true;
        if (inventoryChoices.isEmpty()) {
            showActionHint("No items available in your inventory.");
        }
        updateActionButtons();
    }

    private void closeInventoryPicker() {
        inventoryPickerVisible = false;
        setInventoryPickerWidgetsVisible(false);
        setMainWidgetsVisible(true);
        updateActionButtons();
    }

    private void setInventoryPickerWidgetsVisible(boolean visible) {
        if (inventorySelectButton != null) {
            inventorySelectButton.visible = visible;
            inventorySelectButton.active = visible && !inventoryChoices.isEmpty();
        }
        if (inventoryCancelButton != null) {
            inventoryCancelButton.visible = visible;
            inventoryCancelButton.active = visible;
        }
    }

    private void setMainWidgetsVisible(boolean visible) {
        if (quantityInput != null) {
            quantityInput.visible = visible;
        }
        if (registerItemButton != null) {
            registerItemButton.visible = visible;
        }
        if (buyButton != null) {
            buyButton.visible = visible;
        }
        if (sellButton != null) {
            sellButton.visible = visible;
        }
        if (cancelSellButton != null) {
            cancelSellButton.visible = visible;
        }
        if (quantityOneButton != null) {
            quantityOneButton.visible = visible;
        }
        if (quantityTenButton != null) {
            quantityTenButton.visible = visible;
        }
        if (quantityStackButton != null) {
            quantityStackButton.visible = visible;
        }
        if (refreshButton != null) {
            refreshButton.visible = visible;
        }
        if (closeButton != null) {
            closeButton.visible = visible;
        }
    }

    private void loadInventoryChoices() {
        inventoryChoices.clear();
        if (inventoryListPanel == null) {
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (player == null) {
            inventoryListPanel.setEntries(List.of());
            return;
        }

        List<Component> entryTexts = new ArrayList<>();
        List<ItemStack> inventoryItems = player.getInventory().items;
        for (int slot = 0; slot < inventoryItems.size(); slot++) {
            ItemStack stack = inventoryItems.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            String itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            inventoryChoices.add(new InventoryChoice(slot, stack.copy(), itemKey));
            entryTexts.add(Component.literal(stack.getDisplayName().getString() + " x" + stack.getCount() + " (" + itemKey + ")"));
        }

        inventoryListPanel.setEntries(entryTexts);
        if (!entryTexts.isEmpty()) {
            inventoryListPanel.setSelectedIndex(0);
        }
    }

    private void applySelectedInventoryItem() {
        if (inventoryChoices.isEmpty() || inventoryListPanel == null) {
            closeInventoryPicker();
            return;
        }

        int selectedIndex = Math.max(0, Math.min(inventoryListPanel.selectedIndex(), inventoryChoices.size() - 1));
        InventoryChoice choice = inventoryChoices.get(selectedIndex);
        if (quantityInput != null) {
            quantityInput.setValue(Integer.toString(Math.max(1, choice.stack().getCount())));
        }
        closeInventoryPicker();
        requestRegisterItem(choice);
    }

    private boolean selectShopItemByInventoryKey(String itemKey) {
        if (itemKey == null || itemKey.isBlank() || itemListPanel == null || items.isEmpty()) {
            return false;
        }
        String shortKey = itemKey.contains(":") ? itemKey.substring(itemKey.indexOf(':') + 1) : itemKey;
        for (int i = 0; i < items.size(); i++) {
            ShopItemViewData item = items.get(i);
            if (itemKey.equalsIgnoreCase(item.itemId()) || shortKey.equalsIgnoreCase(item.itemId())) {
                itemListPanel.setSelectedIndex(i);
                selectedItem = item;
                requestItemDetail(item.itemId(), false);
                requestAutoPreviews();
                updateActionButtons();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Integer validatedQuantity() {
        if (quantityInput == null) {
            quantityError = "Quantity input is unavailable.";
            return null;
        }

        String raw = quantityInput.getValue();
        if (raw == null || raw.isBlank()) {
            quantityError = "Enter quantity.";
            return null;
        }

        try {
            int quantity = Integer.parseInt(raw);
            if (quantity <= 0) {
                quantityError = "Quantity must be 1 or more.";
                return null;
            }
            quantityError = null;
            return quantity;
        } catch (NumberFormatException ex) {
            quantityError = "Quantity must be a number.";
            return null;
        }
    }

    private boolean hasMatchingPreview(String transactionType, int quantity) {
        ShopPreviewViewData preview = "buy".equalsIgnoreCase(transactionType) ? buyPreview : sellPreview;
        return selectedItem != null
                && preview != null
                && transactionType.equalsIgnoreCase(preview.transactionType())
                && selectedItem.itemId().equals(preview.itemId())
                && quantity == preview.quantity();
    }

    private void clearPreviews() {
        buyPreview = null;
        sellPreview = null;
        pendingPreviewBuyRequestId = null;
        pendingPreviewSellRequestId = null;
        previewLoading = false;
    }

    private void handleListResponse(UiResponsePayload payload) {
        if (pendingListRequestId != null && !pendingListRequestId.equals(payload.requestId())) {
            return;
        }
        pendingListRequestId = null;
        listLoading = false;
        setLoading(false);

        if (!payload.success()) {
            items.clear();
            selectedItem = null;
            clearPreviews();
            showFailure("Could not load shop items.");
            updateListEntries();
            updateActionButtons();
            return;
        }

        try {
            String previousItemId = selectedItem == null ? null : selectedItem.itemId();
            List<ShopItemViewData> fetchedItems = ShopJsonParser.parseItems(payload.dataJson());
            items.clear();
            items.addAll(fetchedItems);
            setError(null);
            statusMessage = null;
            updateListEntries();
            restoreOrSelectFirst(previousItemId);
        } catch (Exception ex) {
            items.clear();
            selectedItem = null;
            clearPreviews();
            updateListEntries();
            showFailure("Could not read shop items.");
        }

        updateActionButtons();
    }

    private void handleDetailResponse(UiResponsePayload payload) {
        if (pendingDetailRequestId != null && !pendingDetailRequestId.equals(payload.requestId())) {
            return;
        }
        pendingDetailRequestId = null;

        if (!payload.success()) {
            showFailure("Could not load selected item.");
            return;
        }

        try {
            ShopItemViewData detail = ShopJsonParser.parseItem(payload.dataJson());
            upsertItem(detail);
            selectedItem = detail;
            updateListEntries();
            updateSelectionByItemId(detail.itemId());
            setError(null);
        } catch (Exception ex) {
            showFailure("Could not read selected item.");
        }
    }

    private void handlePreviewResponse(UiResponsePayload payload) {
        boolean isBuy = payload.action() == UiAction.SHOP_PREVIEW_BUY;
        String pendingId = isBuy ? pendingPreviewBuyRequestId : pendingPreviewSellRequestId;
        if (pendingId != null && !pendingId.equals(payload.requestId())) {
            return;
        }
        if (isBuy) {
            pendingPreviewBuyRequestId = null;
        } else {
            pendingPreviewSellRequestId = null;
        }
        previewLoading = pendingPreviewBuyRequestId != null || pendingPreviewSellRequestId != null;

        if (!payload.success()) {
            showFailure("Could not check price.");
            updateActionButtons();
            return;
        }

        try {
            ShopPreviewViewData preview = ShopJsonParser.parsePreview(payload.dataJson());
            if (isBuy) {
                buyPreview = preview;
            } else {
                sellPreview = preview;
            }
            statusMessage = null;
            setError(null);
        } catch (Exception ex) {
            showFailure("Could not read price quote.");
        }
        updateActionButtons();
    }

    private void handleTradeResponse(UiResponsePayload payload) {
        if (pendingTradeRequestId != null && !pendingTradeRequestId.equals(payload.requestId())) {
            return;
        }
        pendingTradeRequestId = null;
        tradeLoading = false;

        if (!payload.success()) {
            showFailure("Trade failed. Please try again.");
            updateActionButtons();
            return;
        }

        String transactionType = payload.action() == UiAction.SHOP_BUY ? "buy" : "sell";

        try {
            lastTrade = ShopJsonParser.parseTrade(payload.dataJson(), transactionType);
            statusMessage = "buy".equals(transactionType) ? "Purchase completed." : "Sale completed.";
            setError(null);
            requestItemList(true);
        } catch (Exception ex) {
            showFailure("Trade completed, but confirmation could not be read.");
        }
        updateActionButtons();
    }

    private void handleListingActionResponse(UiResponsePayload payload) {
        if (payload.action() == UiAction.SHOP_REGISTER) {
            if (pendingRegisterRequestId != null && !pendingRegisterRequestId.equals(payload.requestId())) {
                return;
            }
            pendingRegisterRequestId = null;
        } else if (payload.action() == UiAction.SHOP_CANCEL_SELL) {
            if (pendingCancelSellRequestId != null && !pendingCancelSellRequestId.equals(payload.requestId())) {
                return;
            }
            pendingCancelSellRequestId = null;
        } else {
            return;
        }

        listingActionLoading = false;
        if (!payload.success()) {
            showFailure("Could not update listing.");
            updateActionButtons();
            return;
        }

        if (payload.action() == UiAction.SHOP_REGISTER) {
            statusMessage = "Item listed for sale.";
            tryApplyListingFromActionResponse(payload.dataJson());
        } else {
            statusMessage = "Listing canceled.";
        }
        requestItemList(true);
        updateActionButtons();
    }

    private void updateListEntries() {
        if (itemListPanel == null) {
            return;
        }
        itemListPanel.setEntries(items);
        if (items.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.shop.no_items"));
        } else {
            setEmpty(null);
        }
    }

    private void restoreOrSelectFirst(@Nullable String previousItemId) {
        if (itemListPanel == null || items.isEmpty()) {
            selectedItem = null;
            return;
        }

        int selectedIndex = 0;
        if (previousItemId != null) {
            for (int i = 0; i < items.size(); i++) {
                if (previousItemId.equals(items.get(i).itemId())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        itemListPanel.setSelectedIndex(selectedIndex);
        selectedItem = items.get(selectedIndex);
        requestItemDetail(selectedItem.itemId(), false);
        requestAutoPreviews();
    }

    private void updateSelectionByItemId(String itemId) {
        if (itemListPanel == null || itemId == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).itemId())) {
                itemListPanel.setSelectedIndex(i);
                return;
            }
        }
    }

    private void upsertItem(ShopItemViewData item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).itemId().equals(item.itemId())) {
                items.set(i, item);
                return;
            }
        }
        items.add(item);
    }

    private void showActionHint(String message) {
        statusMessage = message;
        setError(null);
    }

    private void showFailure(String message) {
        statusMessage = null;
        setError(message);
    }

    private void tryApplyListingFromActionResponse(@Nullable String dataJson) {
        if (dataJson == null || dataJson.isBlank()) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(dataJson);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject listing = parsed.getAsJsonObject().getAsJsonObject("listing");
            if (listing == null) {
                return;
            }
            ShopItemViewData listingItem = ShopJsonParser.parseItem(listing.toString());
            upsertItem(listingItem);
            selectedItem = listingItem;
            updateListEntries();
            updateSelectionByItemId(listingItem.itemId());
        } catch (Exception ignored) {
            // list refresh will still synchronize from server
        }
    }

    private void updateActionButtons() {
        Integer quantity = validatedQuantity();
        boolean hasItem = selectedItem != null;
        boolean validQuantity = quantity != null;
        boolean playerListedItem = selectedItem != null && selectedItem.playerListed();
        boolean busy = listLoading || tradeLoading || listingActionLoading || inventoryPickerVisible;

        if (buyButton != null) {
            buyButton.active = hasItem
                    && validQuantity
                    && !busy
                    && !previewLoading
                    && hasMatchingPreview("buy", quantity == null ? -1 : quantity);
        }
        if (sellButton != null) {
            sellButton.active = hasItem
                    && validQuantity
                    && !busy
                    && !previewLoading
                    && hasMatchingPreview("sell", quantity == null ? -1 : quantity);
        }
        if (registerItemButton != null) {
            registerItemButton.active = !listLoading && !tradeLoading && !listingActionLoading && !inventoryPickerVisible;
        }
        if (cancelSellButton != null) {
            cancelSellButton.active = playerListedItem && !busy;
        }
        if (quantityOneButton != null) {
            quantityOneButton.active = !busy;
        }
        if (quantityTenButton != null) {
            quantityTenButton.active = !busy;
        }
        if (quantityStackButton != null) {
            quantityStackButton.active = !busy;
        }
        if (refreshButton != null) {
            refreshButton.active = !busy;
        }
        if (inventorySelectButton != null) {
            inventorySelectButton.active = inventoryPickerVisible && !inventoryChoices.isEmpty() && !listingActionLoading;
        }
        if (inventoryCancelButton != null) {
            inventoryCancelButton.active = inventoryPickerVisible;
        }
    }

    private void layoutActionWidgets() {
        if (quantityInput == null) {
            return;
        }

        int contentLeft = actionX + 8;
        int contentRight = actionX + actionWidth - 8;
        int rowOneY = actionY + 16;
        int rowTwoY = Math.min(
                actionY + actionHeight - 22,
                Math.max(rowOneY + 22, actionY + 40)
        );

        int quickGap = 3;
        int quickZoneWidth = clampInt(actionWidth / 2, 66, 102);
        int quickStartX = contentRight - quickZoneWidth;
        int quickWidth = Math.max(18, (quickZoneWidth - quickGap * 2) / 3);

        int inputX = contentLeft + 58;
        int inputWidth = quickStartX - quickGap - inputX;
        if (inputWidth < 36) {
            inputWidth = 36;
            inputX = Math.max(contentLeft + 34, quickStartX - quickGap - inputWidth);
        }

        quantityInput.setPosition(inputX, rowOneY + 2);
        quantityInput.setWidth(Math.max(34, inputWidth));

        if (quantityOneButton != null) {
            quantityOneButton.setPosition(quickStartX, rowOneY);
            quantityOneButton.setWidth(quickWidth);
        }
        if (quantityTenButton != null) {
            quantityTenButton.setPosition(quickStartX + quickWidth + quickGap, rowOneY);
            quantityTenButton.setWidth(quickWidth);
        }
        if (quantityStackButton != null) {
            quantityStackButton.setPosition(quickStartX + (quickWidth + quickGap) * 2, rowOneY);
            quantityStackButton.setWidth(quickWidth);
        }

        int gap = 4;
        int availableWidth = actionWidth - 16 - gap * 3;
        int buttonWidth = Math.max(22, availableWidth / 4);
        int startX = contentLeft;

        if (registerItemButton != null) {
            registerItemButton.setPosition(startX, rowTwoY);
            registerItemButton.setWidth(buttonWidth);
        }
        if (buyButton != null) {
            buyButton.setPosition(startX + (buttonWidth + gap), rowTwoY);
            buyButton.setWidth(buttonWidth);
        }
        if (sellButton != null) {
            sellButton.setPosition(startX + (buttonWidth + gap) * 2, rowTwoY);
            sellButton.setWidth(buttonWidth);
        }
        if (cancelSellButton != null) {
            cancelSellButton.setPosition(startX + (buttonWidth + gap) * 3, rowTwoY);
            cancelSellButton.setWidth(buttonWidth);
        }
    }

    private void recalcLayout() {
        frameWidth = Math.min(560, width - 20);
        frameHeight = Math.min(360, height - 36);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int innerWidth = frameWidth - 20;
        int minRightWidth = 170;
        int preferredListWidth = clampInt(innerWidth * 38 / 100, 118, 210);
        if (innerWidth - preferredListWidth - 8 < minRightWidth) {
            preferredListWidth = Math.max(96, innerWidth - minRightWidth - 8);
        }

        listX = frameX + 10;
        listY = frameY + 34;
        listWidth = Math.max(96, preferredListWidth);
        detailWidth = Math.max(96, innerWidth - listWidth - 8);

        int contentTop = listY;
        int contentBottom = frameY + frameHeight - 10;
        int rightAvailableHeight = Math.max(120, contentBottom - contentTop);
        listHeight = Math.max(64, rightAvailableHeight);

        detailX = listX + listWidth + 8;
        detailY = contentTop;
        actionWidth = detailWidth;

        int gap = 6;
        int minAction = 76;
        actionHeight = clampInt(rightAvailableHeight / 3, minAction, 98);
        detailHeight = Math.max(68, rightAvailableHeight - actionHeight - gap);

        actionX = detailX;
        actionY = detailY + detailHeight + gap;
        if (actionY + actionHeight > contentBottom) {
            actionHeight = Math.max(48, contentBottom - actionY);
        }

        inventoryPickerWidth = Math.min(320, width - 40);
        inventoryPickerHeight = Math.min(190, height - 40);
        inventoryPickerX = (width - inventoryPickerWidth) / 2;
        inventoryPickerY = (height - inventoryPickerHeight) / 2;
    }

    private record InventoryChoice(int slot, ItemStack stack, String itemKey) {}
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/shop/ShopActionPanelView.java

```java
package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopActionPanelView {
    private ShopActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            @Nullable String quantityError,
            @Nullable String statusMessage,
            boolean previewLoading,
            boolean tradeLoading
    ) {
        int color = 0xDDE6F9;
        int contentWidth = Math.max(0, width);
        UiTextRender.drawEllipsized(
                graphics,
                font,
                Component.translatable("screen.namanseulfarming.shop.quantity").getString(),
                x,
                y,
                Math.max(0, contentWidth - 60),
                color
        );
        String state = null;
        int stateColor = 0xBFD0E8;
        if (quantityError != null && !quantityError.isBlank()) {
            state = quantityError;
            stateColor = 0xFF7D7D;
        } else if (tradeLoading) {
            state = "Processing trade...";
        } else if (previewLoading) {
            state = "Checking price...";
        } else if (statusMessage != null && !statusMessage.isBlank()) {
            state = statusMessage;
        }

        if (state != null) {
            UiTextRender.drawRightAligned(graphics, font, state, x + contentWidth, y, Math.max(0, contentWidth - 60), stateColor);
        }
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/shop/ShopDetailPanelView.java

```java
package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopDetailPanelView {
    private ShopDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopItemViewData item,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            boolean previewLoading,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = buildLines(item, buyPreview, sellPreview, previewLoading, trade);
        int lineY = y + 4;
        int maxY = y + height - 10;
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, font, line.getString(), contentX, lineY, contentWidth);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(
            @Nullable ShopItemViewData item,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            boolean previewLoading,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = new ArrayList<>();
        if (item == null) {
            lines.add(Component.literal("Select an item."));
            return lines;
        }

        String itemName = (item.itemName() == null || item.itemName().isBlank()) ? item.itemId() : item.itemName();
        lines.add(Component.literal(itemName));
        lines.add(Component.literal("Buy: " + item.currentBuyPrice()));
        lines.add(Component.literal("Sell: " + item.currentSellPrice()));

        if (buyPreview != null) {
            lines.add(Component.literal("Buy x" + buyPreview.quantity() + ": " + buyPreview.netTotalPrice()));
            if (buyPreview.feeAmount() > 0) {
                lines.add(Component.literal("Buy Fee: " + buyPreview.feeAmount()));
            }
            if (Boolean.FALSE.equals(buyPreview.canAfford())) {
                lines.add(Component.literal("Not enough balance."));
            }
        }

        if (sellPreview != null) {
            lines.add(Component.literal("Sell x" + sellPreview.quantity() + ": " + sellPreview.netTotalPrice()));
            if (sellPreview.feeAmount() > 0) {
                lines.add(Component.literal("Sell Fee: " + sellPreview.feeAmount()));
            }
        }

        if (buyPreview == null && sellPreview == null) {
            lines.add(Component.literal(previewLoading
                    ? "Checking quote..."
                    : "Set quantity for quote."));
        }

        int stock = Math.max(0, item.stockQuantity());
        lines.add(Component.literal("Stock: " + stock));
        if (item.playerListed()) {
            lines.add(Component.literal("Listed by you: " + Math.max(1, item.listingQuantity())));
        }

        if (trade != null && item.itemId().equals(trade.itemId())) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "Bought" : "Sold";
            lines.add(Component.literal("Last: " + action + " x" + trade.quantity()));
            lines.add(Component.literal("Last Net: " + trade.netTotalPrice()));
        }

        return lines;
    }

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 20) {
                int labelWidth = Math.max(48, Math.min(108, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, line, x, y, width, 0xEAF1FF);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/shop/ShopItemListPanel.java

```java
package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ShopItemListPanel {
    private static final int HEADER_HEIGHT = 14;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<ShopItemViewData> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public ShopItemListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 18);
    }

    public ShopItemListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<ShopItemViewData> newEntries) {
        this.entries = new ArrayList<>(newEntries == null ? Collections.emptyList() : newEntries);
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (selectedIndex >= entries.size()) {
            selectedIndex = entries.size() - 1;
        }
        clampScroll();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        clampScroll();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x99202633);
        graphics.fill(x, y, x + width, y + 1, 0xFF4D5E7D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4D5E7D);
        graphics.fill(x, y, x + 1, y + height, 0xFF4D5E7D);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF4D5E7D);

        renderHeader(graphics, font);
        graphics.fill(x + 2, y + HEADER_HEIGHT, x + width - 2, y + HEADER_HEIGHT + 1, 0x995A6A8A);

        int viewTop = y + HEADER_HEIGHT + 2;
        int viewBottom = y + height - 2;
        graphics.enableScissor(x + 1, viewTop, x + width - 1, viewBottom);
        int baseY = viewTop + 2 - scrollOffset;

        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight <= viewTop || rowY >= viewBottom) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + rowHeight - 1, 0xAA3A4A6A);
            }

            ShopItemViewData item = entries.get(i);
            ItemStack iconStack = resolveIconStack(item.itemId());
            int iconY = rowY + Math.max(0, (rowHeight - 16) / 2);
            int textY = rowY + Math.max(0, (rowHeight - font.lineHeight) / 2);
            graphics.renderItem(iconStack, x + 4, iconY);

            String name = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
            int stock = Math.max(0, item.stockQuantity());
            if (item.playerListed()) {
                name = name + " (listed " + Math.max(1, item.listingQuantity()) + ", stock " + stock + ")";
            } else {
                name = name + " (stock " + stock + ")";
            }
            int sellRight = x + width - 8;
            int sellWidth = 34;
            int buyRight = sellRight - sellWidth - 6;
            int buyWidth = 34;
            int itemColumnEnd = buyRight - buyWidth - 8;
            UiTextRender.drawEllipsized(graphics, font, name, x + 24, textY, Math.max(24, itemColumnEnd - (x + 24)), 0xFFFFFF);

            String buyText = Integer.toString(item.currentBuyPrice());
            String sellText = Integer.toString(item.currentSellPrice());
            UiTextRender.drawRightAligned(graphics, font, buyText, buyRight, textY, buyWidth, 0xE8F0FF);
            UiTextRender.drawRightAligned(graphics, font, sellText, sellRight, textY, sellWidth, 0xE8F0FF);
        }

        graphics.disableScissor();
    }

    private void renderHeader(GuiGraphics graphics, Font font) {
        int color = 0xD7E4FF;
        graphics.drawString(font, Component.literal("Item"), x + 6, y + 3, color, false);
        UiTextRender.drawRightAligned(graphics, font, "Buy", x + width - 48, y + 3, 34, color);
        UiTextRender.drawRightAligned(graphics, font, "Sell", x + width - 8, y + 3, 34, color);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }

        if (mouseY < y + HEADER_HEIGHT + 2) {
            return false;
        }

        int relativeY = (int) mouseY - (y + HEADER_HEIGHT + 4) + scrollOffset;
        int clickedIndex = relativeY / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < entries.size()) {
            selectedIndex = clickedIndex;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int step = Math.max(1, rowHeight - 2);
        scrollOffset -= (int) Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int viewportHeight = height - HEADER_HEIGHT - 6;
        int maxOffset = Math.max(0, entries.size() * rowHeight - viewportHeight);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static ItemStack resolveIconStack(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return new ItemStack(Items.BARRIER);
        }

        Item item = null;
        ResourceLocation direct = ResourceLocation.tryParse(itemId);
        if (direct != null) {
            item = BuiltInRegistries.ITEM.get(direct);
        }

        if (item == null || item == Items.AIR) {
            ResourceLocation namespaced = ResourceLocation.tryParse("minecraft:" + itemId);
            if (namespaced != null) {
                item = BuiltInRegistries.ITEM.get(namespaced);
            }
        }

        if (item == null || item == Items.AIR) {
            item = Items.BARRIER;
        }
        return new ItemStack(item);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/shop/ShopPreviewPanelView.java

```java
package com.namanseul.farmingmod.client.ui.shop;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ShopPreviewPanelView {
    private ShopPreviewPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = buildLines(buyPreview, sellPreview, trade);
        int lineY = y + 4;
        int maxY = y + height - 10;
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            UiTextRender.drawEllipsized(graphics, font, line.getString(), contentX, lineY, contentWidth, 0xEAF1FF);
            lineY += 12;
        }
    }

    public static List<Component> buildLines(
            @Nullable ShopPreviewViewData buyPreview,
            @Nullable ShopPreviewViewData sellPreview,
            @Nullable ShopTradeViewData trade
    ) {
        List<Component> lines = new ArrayList<>();
        if (buyPreview == null && sellPreview == null && trade == null) {
            lines.add(Component.literal("Select item and quantity."));
            return lines;
        }

        if (buyPreview != null) {
            lines.add(Component.literal("Buy x" + buyPreview.quantity() + ": " + buyPreview.netTotalPrice()));
            if (buyPreview.feeAmount() > 0) {
                lines.add(Component.literal("Buy Fee: " + buyPreview.feeAmount()));
            }
        }
        if (sellPreview != null) {
            lines.add(Component.literal("Sell x" + sellPreview.quantity() + ": " + sellPreview.netTotalPrice()));
            if (sellPreview.feeAmount() > 0) {
                lines.add(Component.literal("Sell Fee: " + sellPreview.feeAmount()));
            }
        }
        if (trade != null) {
            String action = "buy".equalsIgnoreCase(trade.transactionType()) ? "Bought" : "Sold";
            lines.add(Component.literal("Last trade: " + action + " x" + trade.quantity()));
        }

        return lines;
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/screen/MailScreen.java

```java
package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.mail.MailActionPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailClaimResultViewData;
import com.namanseul.farmingmod.client.ui.mail.MailDetailPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailJsonParser;
import com.namanseul.farmingmod.client.ui.mail.MailListPanel;
import com.namanseul.farmingmod.client.ui.mail.MailRewardPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailViewData;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiMessageBanner;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailScreen extends BaseGameScreen {
    private final Screen returnScreen;
    private final List<MailViewData> mails = new ArrayList<>();

    private MailListPanel mailListPanel;
    private MailViewData selectedMail;
    private MailClaimResultViewData lastClaim;

    private Button claimButton;

    private String pendingListRequestId;
    private String pendingDetailRequestId;
    private String pendingClaimRequestId;

    private boolean listLoading;
    private boolean claimLoading;
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

    private int rewardX;
    private int rewardY;
    private int rewardWidth;
    private int rewardHeight;

    private int actionX;
    private int actionY;
    private int actionWidth;
    private int actionHeight;

    public MailScreen(@Nullable Screen returnScreen) {
        super(Component.translatable("screen.namanseulfarming.mail.title"));
        this.returnScreen = returnScreen;
    }

    public static MailScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MailScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        MailScreen screen = new MailScreen(parent);
        minecraft.setScreen(screen);
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();

        initCommonButtons(frameX + frameWidth - 4, frameY + 8);
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.mail.back"));
        }

        mailListPanel = new MailListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);
        initActionWidgets();
        requestMailList(false);
    }

    @Override
    protected void onRefreshPressed() {
        requestMailList(true);
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
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        layoutActionWidgets();
        updateActionButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, listX, listY, listWidth, listHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.mail.list"), listX + 6, listY + 6);
        if (mailListPanel != null) {
            mailListPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.mail.detail"), detailX + 6, detailY + 6);
        renderClipped(graphics, detailX, detailY, detailWidth, detailHeight, () ->
                MailDetailPanelView.render(graphics, font, detailX + 2, detailY + 18, detailWidth - 4, detailHeight - 20, selectedMail)
        );

        renderPanel(graphics, rewardX, rewardY, rewardWidth, rewardHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.mail.reward"), rewardX + 6, rewardY + 6);
        renderClipped(graphics, rewardX, rewardY, rewardWidth, rewardHeight, () ->
                MailRewardPanelView.render(
                        graphics,
                        font,
                        rewardX + 2,
                        rewardY + 18,
                        rewardWidth - 4,
                        rewardHeight - 20,
                        selectedMail,
                        lastClaim
                )
        );

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.mail.actions"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () ->
                MailActionPanelView.render(graphics, font, actionX + 8, actionY + 34, actionWidth - 16, statusMessage, claimLoading)
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mailListPanel != null && mailListPanel.mouseClicked(mouseX, mouseY, button)) {
            onSelectedIndexChanged();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mailListPanel != null && mailListPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void handleServerResponse(UiResponsePayload payload) {
        switch (payload.action()) {
            case MAIL_LIST, MAIL_REFRESH -> handleListResponse(payload);
            case MAIL_DETAIL -> handleDetailResponse(payload);
            case MAIL_CLAIM -> handleClaimResponse(payload);
            default -> {
                // ignore unrelated actions
            }
        }
    }

    private void initActionWidgets() {
        claimButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.mail.claim"),
                0,
                0,
                96,
                20,
                button -> requestClaim()
        ));
        layoutActionWidgets();
    }

    private void requestMailList(boolean forceRefresh) {
        listLoading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.mail.loading_mails"));
        setError(null);
        statusMessage = null;
        pendingListRequestId = forceRefresh
                ? UiClientNetworking.requestMailRefresh()
                : UiClientNetworking.requestMailList(false);
        updateActionButtons();
    }

    private void requestMailDetail(String mailId, boolean forceRefresh) {
        pendingDetailRequestId = UiClientNetworking.requestMailDetail(mailId, forceRefresh);
    }

    private void requestClaim() {
        if (selectedMail == null) {
            showWarning("select a mail first");
            return;
        }
        if (selectedMail.claimed()) {
            showWarning("mail is already claimed");
            return;
        }
        if (!selectedMail.hasReward()) {
            showWarning("this mail has no claimable reward");
            return;
        }
        if (claimLoading) {
            return;
        }

        claimLoading = true;
        setError(null);
        statusMessage = null;
        pendingClaimRequestId = UiClientNetworking.requestMailClaim(selectedMail.id());
        updateActionButtons();
    }

    private void onSelectedIndexChanged() {
        if (mailListPanel == null || mails.isEmpty()) {
            selectedMail = null;
            return;
        }

        int selectedIndex = Math.max(0, Math.min(mailListPanel.selectedIndex(), mails.size() - 1));
        selectedMail = mails.get(selectedIndex);
        statusMessage = null;
        requestMailDetail(selectedMail.id(), false);
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
            mails.clear();
            selectedMail = null;
            setError(payload.error() == null ? "mail list request failed" : payload.error());
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.mail.banner.list_failed")));
            updateListEntries();
            updateActionButtons();
            return;
        }

        try {
            String previousMailId = selectedMail == null ? null : selectedMail.id();
            List<MailViewData> fetched = MailJsonParser.parseList(payload.dataJson());
            mails.clear();
            mails.addAll(fetched);
            setError(null);
            updateListEntries();
            restoreOrSelectFirst(previousMailId);
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.INFO,
                    Component.translatable("screen.namanseulfarming.mail.banner.list_loaded")));
        } catch (Exception ex) {
            mails.clear();
            selectedMail = null;
            updateListEntries();
            setError("failed to parse mail list response");
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.mail.banner.list_failed")));
        }

        updateActionButtons();
    }

    private void handleDetailResponse(UiResponsePayload payload) {
        if (pendingDetailRequestId != null && !pendingDetailRequestId.equals(payload.requestId())) {
            return;
        }
        pendingDetailRequestId = null;

        if (!payload.success()) {
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING,
                    Component.literal(payload.error() == null ? "mail detail request failed" : payload.error())));
            return;
        }

        try {
            MailViewData detail = MailJsonParser.parseDetail(payload.dataJson());
            upsertMail(detail);
            selectedMail = detail;
            updateListEntries();
            updateSelectionByMailId(detail.id());
        } catch (Exception ex) {
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING,
                    Component.literal("failed to parse mail detail")));
        }
    }

    private void handleClaimResponse(UiResponsePayload payload) {
        if (pendingClaimRequestId != null && !pendingClaimRequestId.equals(payload.requestId())) {
            return;
        }
        pendingClaimRequestId = null;
        claimLoading = false;

        if (!payload.success()) {
            setError(payload.error() == null ? "mail claim failed" : payload.error());
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.literal(payload.error() == null ? "Mail claim failed." : payload.error())));
            updateActionButtons();
            return;
        }

        try {
            MailClaimResultViewData claim = MailJsonParser.parseClaim(payload.dataJson());
            lastClaim = claim;
            String rewardText = claim.rewardAmount() == null ? "-" : Integer.toString(claim.rewardAmount());
            statusMessage = "claim success (reward " + rewardText + ")";
            setError(null);
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.INFO,
                    Component.translatable("screen.namanseulfarming.mail.banner.claim_success", rewardText)));

            if (claim.mail() != null) {
                upsertMail(claim.mail());
                selectedMail = claim.mail();
                updateListEntries();
                updateSelectionByMailId(claim.mail().id());
            }

            requestMailList(true);
        } catch (Exception ex) {
            setError("failed to parse mail claim response");
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.literal("Failed to parse claim result")));
        }
        updateActionButtons();
    }

    private void updateListEntries() {
        if (mailListPanel == null) {
            return;
        }
        mailListPanel.setEntries(mails);
        if (mails.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.mail.no_mails"));
        } else {
            setEmpty(null);
        }
    }

    private void restoreOrSelectFirst(@Nullable String previousMailId) {
        if (mailListPanel == null || mails.isEmpty()) {
            selectedMail = null;
            return;
        }

        int selectedIndex = 0;
        if (previousMailId != null) {
            for (int i = 0; i < mails.size(); i++) {
                if (previousMailId.equals(mails.get(i).id())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        mailListPanel.setSelectedIndex(selectedIndex);
        selectedMail = mails.get(selectedIndex);
        requestMailDetail(selectedMail.id(), false);
    }

    private void updateSelectionByMailId(String mailId) {
        if (mailListPanel == null || mailId == null) {
            return;
        }
        for (int i = 0; i < mails.size(); i++) {
            if (mailId.equals(mails.get(i).id())) {
                mailListPanel.setSelectedIndex(i);
                return;
            }
        }
    }

    private void upsertMail(MailViewData mail) {
        for (int i = 0; i < mails.size(); i++) {
            if (mails.get(i).id().equals(mail.id())) {
                mails.set(i, mail);
                return;
            }
        }
        mails.add(mail);
    }

    private void showWarning(String message) {
        setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING, Component.literal(message)));
    }

    private void updateActionButtons() {
        boolean busy = listLoading || claimLoading;
        boolean claimable = selectedMail != null && selectedMail.hasReward() && !selectedMail.claimed();

        if (claimButton != null) {
            claimButton.active = claimable && !busy;
        }
        if (refreshButton != null) {
            refreshButton.active = !busy;
        }
        if (closeButton != null) {
            closeButton.active = !claimLoading;
        }
    }

    private void layoutActionWidgets() {
        if (claimButton == null) {
            return;
        }
        claimButton.setPosition(actionX + 8, actionY + 16);
        claimButton.setWidth(Math.max(44, actionWidth - 16));
    }

    private void recalcLayout() {
        frameWidth = Math.min(560, width - 20);
        frameHeight = Math.min(360, height - 36);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int innerWidth = frameWidth - 20;
        int minRightWidth = 170;
        int preferredListWidth = clampInt(innerWidth * 38 / 100, 118, 210);
        if (innerWidth - preferredListWidth - 8 < minRightWidth) {
            preferredListWidth = Math.max(96, innerWidth - minRightWidth - 8);
        }

        listX = frameX + 10;
        listY = frameY + 34;
        listWidth = Math.max(96, preferredListWidth);
        detailWidth = Math.max(96, innerWidth - listWidth - 8);

        int contentTop = listY;
        int contentBottom = frameY + frameHeight - 10;
        int rightAvailableHeight = Math.max(120, contentBottom - contentTop);
        listHeight = Math.max(64, rightAvailableHeight);

        detailX = listX + listWidth + 8;
        detailY = contentTop;

        actionWidth = detailWidth;
        rewardWidth = detailWidth;

        int gap = 6;
        int minDetail = 52;
        int minReward = 48;
        int minAction = 64;
        actionHeight = clampInt(rightAvailableHeight / 3, minAction, 84);
        rewardHeight = clampInt(rightAvailableHeight / 3, minReward, 96);
        int detailCandidate = rightAvailableHeight - actionHeight - rewardHeight - gap * 2;
        if (detailCandidate < minDetail) {
            int deficit = minDetail - detailCandidate;
            int reduceReward = Math.min(deficit, rewardHeight - minReward);
            rewardHeight -= reduceReward;
            deficit -= reduceReward;

            int reduceAction = Math.min(deficit, actionHeight - minAction);
            actionHeight -= reduceAction;
            deficit -= reduceAction;
        }
        detailHeight = Math.max(40, rightAvailableHeight - actionHeight - rewardHeight - gap * 2);

        rewardX = detailX;
        rewardY = detailY + detailHeight + gap;

        actionX = detailX;
        actionY = rewardY + rewardHeight + gap;
        if (actionY + actionHeight > contentBottom) {
            actionHeight = Math.max(48, contentBottom - actionY);
        }
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/mail/MailActionPanelView.java

```java
package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailActionPanelView {
    private MailActionPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            @Nullable String statusMessage,
            boolean claimLoading
    ) {
        int contentWidth = Math.max(0, width);
        int color = 0xDDE6F9;
        if (claimLoading) {
            UiTextRender.drawEllipsized(
                    graphics,
                    font,
                    Component.translatable("screen.namanseulfarming.mail.claim_loading").getString(),
                    x,
                    y,
                    contentWidth,
                    color
            );
            y += 12;
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            UiTextRender.drawEllipsized(graphics, font, statusMessage, x, y, contentWidth, 0xBFD0E8);
        }
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/mail/MailDetailPanelView.java

```java
package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailDetailPanelView {
    private MailDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable MailViewData mail
    ) {
        List<Component> lines = buildLines(mail);
        int lineY = y + 4;
        int maxY = y + height - 10;
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, font, line.getString(), contentX, lineY, contentWidth);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(@Nullable MailViewData mail) {
        List<Component> lines = new ArrayList<>();
        if (mail == null) {
            lines.add(Component.translatable("screen.namanseulfarming.mail.no_selection"));
            return lines;
        }

        lines.add(Component.literal("title: " + safe(mail.title())));
        lines.add(Component.literal("mailId: " + safe(mail.id())));
        lines.add(Component.literal("mailType: " + safe(mail.mailType())));
        lines.add(Component.literal("claimed: " + mail.claimed()));
        lines.add(Component.literal("hasReward: " + mail.hasReward()));
        lines.add(Component.literal("rewardAmount: " + numberOrDash(mail.rewardAmount())));
        lines.add(Component.literal("rewardType: " + safe(mail.rewardType())));
        lines.add(Component.literal("sentAt: " + safe(mail.createdAtText())));
        if (mail.claimedAtText() != null && !mail.claimedAtText().isBlank()) {
            lines.add(Component.literal("claimedAt: " + mail.claimedAtText()));
        }
        if (mail.itemRewardItemId() != null && !mail.itemRewardItemId().isBlank()) {
            lines.add(Component.literal("itemReward: " + mail.itemRewardItemId()
                    + " x" + numberOrDash(mail.itemRewardQuantity())));
        }

        lines.add(Component.literal("message: " + safe(mail.message())));
        return lines;
    }

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 20) {
                int labelWidth = Math.max(52, Math.min(116, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, line, x, y, width, 0xEAF1FF);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String numberOrDash(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/mail/MailListPanel.java

```java
package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class MailListPanel {
    private static final int HEADER_HEIGHT = 14;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private List<MailViewData> entries = List.of();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public MailListPanel(int x, int y, int width, int height) {
        this(x, y, width, height, 22);
    }

    public MailListPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
    }

    public void setEntries(List<MailViewData> newEntries) {
        this.entries = new ArrayList<>(newEntries == null ? Collections.emptyList() : newEntries);
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        if (selectedIndex >= entries.size()) {
            selectedIndex = entries.size() - 1;
        }
        clampScroll();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (entries.isEmpty()) {
            selectedIndex = 0;
            scrollOffset = 0;
            return;
        }
        selectedIndex = Math.max(0, Math.min(index, entries.size() - 1));
        clampScroll();
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0x99202633);
        graphics.fill(x, y, x + width, y + 1, 0xFF4D5E7D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF4D5E7D);
        graphics.fill(x, y, x + 1, y + height, 0xFF4D5E7D);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF4D5E7D);

        renderHeader(graphics, font);
        graphics.fill(x + 2, y + HEADER_HEIGHT, x + width - 2, y + HEADER_HEIGHT + 1, 0x995A6A8A);

        int viewTop = y + HEADER_HEIGHT + 2;
        int viewBottom = y + height - 2;
        graphics.enableScissor(x + 1, viewTop, x + width - 1, viewBottom);
        int baseY = viewTop + 2 - scrollOffset;

        for (int i = 0; i < entries.size(); i++) {
            int rowY = baseY + i * rowHeight;
            if (rowY + rowHeight <= viewTop || rowY >= viewBottom) {
                continue;
            }

            if (i == selectedIndex) {
                graphics.fill(x + 2, rowY, x + width - 2, rowY + rowHeight - 1, 0xAA3A4A6A);
            }

            MailViewData mail = entries.get(i);
            String titlePrefix = mail.recent() ? "[NEW] " : "";
            String titleText = titlePrefix + safe(mail.title());
            String summaryText = buildSummaryLine(mail);

            UiTextRender.drawEllipsized(graphics, font, titleText, x + 6, rowY + 2, width - 12, 0xFFFFFF);
            UiTextRender.drawEllipsized(graphics, font, summaryText, x + 6, rowY + 12, width - 12, 0xBFD0E8);
        }

        graphics.disableScissor();
    }

    private static String buildSummaryLine(MailViewData mail) {
        String claimed = mail.claimed() ? "claimed" : "unclaimed";
        String reward;
        if (!mail.hasReward()) {
            reward = "reward:none";
        } else if ("item".equalsIgnoreCase(mail.rewardType())) {
            reward = "reward:item";
        } else {
            reward = "reward:" + (mail.rewardAmount() == null ? "gold" : mail.rewardAmount());
        }
        return safe(mail.mailType()) + " | " + reward + " | " + claimed;
    }

    private void renderHeader(GuiGraphics graphics, Font font) {
        graphics.drawString(font, Component.literal("Mail"), x + 6, y + 3, 0xD7E4FF, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY) || entries.isEmpty()) {
            return false;
        }
        if (mouseY < y + HEADER_HEIGHT + 2) {
            return false;
        }

        int relativeY = (int) mouseY - (y + HEADER_HEIGHT + 4) + scrollOffset;
        int clickedIndex = relativeY / rowHeight;
        if (clickedIndex >= 0 && clickedIndex < entries.size()) {
            selectedIndex = clickedIndex;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int step = Math.max(1, rowHeight - 2);
        scrollOffset -= (int) Math.signum(delta) * step;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int viewportHeight = height - HEADER_HEIGHT - 6;
        int maxOffset = Math.max(0, entries.size() * rowHeight - viewportHeight);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/mail/MailRewardPanelView.java

```java
package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailRewardPanelView {
    private MailRewardPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable MailViewData selectedMail,
            @Nullable MailClaimResultViewData lastClaim
    ) {
        List<Component> lines = buildLines(selectedMail, lastClaim);
        int lineY = y + 4;
        int maxY = y + height - 10;
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, font, line.getString(), contentX, lineY, contentWidth);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(
            @Nullable MailViewData selectedMail,
            @Nullable MailClaimResultViewData lastClaim
    ) {
        List<Component> lines = new ArrayList<>();
        if (selectedMail == null) {
            lines.add(Component.translatable("screen.namanseulfarming.mail.no_selection"));
            return lines;
        }

        lines.add(Component.literal("reward state: " + (selectedMail.hasReward() ? "claimable" : "notification")));
        lines.add(Component.literal("rewardType: " + safe(selectedMail.rewardType())));
        lines.add(Component.literal("rewardAmount: " + numberOrDash(selectedMail.rewardAmount())));
        if (selectedMail.itemRewardItemId() != null) {
            lines.add(Component.literal("itemReward: " + selectedMail.itemRewardItemId()
                    + " x" + numberOrDash(selectedMail.itemRewardQuantity())));
        } else {
            lines.add(Component.literal("itemReward: -"));
        }

        if (lastClaim != null) {
            lines.add(Component.literal("last claim: " + (lastClaim.claimed() ? "success" : "failed")));
            lines.add(Component.literal("last rewardAmount: " + numberOrDash(lastClaim.rewardAmount())));
            lines.add(Component.literal("balanceAfter: " + numberOrDash(lastClaim.balanceAfter())));
        }

        return lines;
    }

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 24) {
                int labelWidth = Math.max(52, Math.min(124, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, line, x, y, width, 0xEAF1FF);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String numberOrDash(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }

    private static String numberOrDash(@Nullable Double value) {
        if (value == null) {
            return "-";
        }
        return String.format("%.0f", value);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/screen/PlayerOverviewScreen.java

```java
package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewData;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewFormatter;
import com.namanseul.farmingmod.client.ui.player.PlayerOverviewParser;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
        int contentX = summaryX + 8;
        int contentWidth = Math.max(0, summaryWidth - 16);
        for (Component line : summaryLines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, line.getString(), contentX, lineY, contentWidth, 0xDDE6F9, 0xEAF1FF);
            lineY += 12;
        }
    }

    private void renderDetailLines(GuiGraphics graphics) {
        int lineY = detailY + 22;
        int maxY = detailY + detailHeight - 10;
        int contentX = detailX + 8;
        int contentWidth = Math.max(0, detailWidth - 16);
        for (Component line : detailLines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, line.getString(), contentX, lineY, contentWidth, 0xDDE6F9, 0xEAF1FF);
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

    private void drawStructuredLine(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int width,
            int labelColor,
            int valueColor
    ) {
        int colon = text.indexOf(':');
        if (colon > 0 && colon < text.length() - 1) {
            String label = text.substring(0, colon + 1).trim();
            String value = text.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 24) {
                int labelWidth = Math.max(54, Math.min(126, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, labelColor, valueColor);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, text, x, y, width, valueColor);
    }
}
```


## namanseulfarming-template-1.21.1/src/main/java/com/namanseul/farmingmod/client/ui/screen/StatusScreen.java

```java
package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.status.StatusJsonParser;
import com.namanseul.farmingmod.client.ui.status.StatusOverviewData;
import com.namanseul.farmingmod.client.ui.status.StatusViewFormatter;
import com.namanseul.farmingmod.client.ui.widget.UiListPanel;
import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
    private UiListPanel listPanel;
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
        initRefreshButton(frameX + frameWidth - 4, frameY + 8);
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.status.back"));
        }

        int tabGap = 4;
        int tabCount = 4;
        int tabAreaWidth = frameWidth - 20;
        int tabWidth = clampInt((tabAreaWidth - tabGap * (tabCount - 1)) / tabCount, 52, 90);
        initTabButtons(frameX + 10, frameY + 34, tabWidth, 20, tabGap);

        listPanel = new UiListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);
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
            setError(payload.error() == null || payload.error().isBlank()
                    ? "Unable to load status overview."
                    : payload.error());
            updateActionButtons();
            return;
        }

        try {
            overviewData = StatusJsonParser.parseOverview(payload.dataJson());
            setError(null);
            updateTabContent();
        } catch (Exception ex) {
            setError("Unable to read status overview data.");
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
        List<Component> summaryLines = StatusViewFormatter.buildSummaryLines(overviewData);
        int lineY = summaryY + 20;
        int maxY = summaryY + summaryHeight - 10;
        int contentX = summaryX + 8;
        int contentWidth = Math.max(0, summaryWidth - 16);
        for (Component line : summaryLines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, line.getString(), contentX, lineY, contentWidth, 0xDDE6F9, 0xEAF1FF);
            lineY += 12;
        }
    }

    private void renderDetailLines(GuiGraphics graphics) {
        int lineY = detailY + 22;
        int maxY = detailY + detailHeight - 10;
        int contentX = detailX + 8;
        int contentWidth = Math.max(0, detailWidth - 16);
        for (Component line : detailLines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, line.getString(), contentX, lineY, contentWidth, 0xDDE6F9, 0xEAF1FF);
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

    private void drawStructuredLine(
            GuiGraphics graphics,
            String text,
            int x,
            int y,
            int width,
            int labelColor,
            int valueColor
    ) {
        int colon = text.indexOf(':');
        if (colon > 0 && colon < text.length() - 1) {
            String label = text.substring(0, colon + 1).trim();
            String value = text.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 24) {
                int labelWidth = Math.max(54, Math.min(126, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, labelColor, valueColor);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, text, x, y, width, valueColor);
    }
}
```


