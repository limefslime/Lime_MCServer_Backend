package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.ui.state.EmptyState;
import com.namanseul.farmingmod.client.ui.state.ErrorState;
import com.namanseul.farmingmod.client.ui.state.LoadingState;
import com.namanseul.farmingmod.client.ui.widget.BalanceHudState;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiMessageBanner;
import com.namanseul.farmingmod.client.ui.widget.UiPanel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class BaseGameScreen extends Screen {
    private static final int COMMON_BUTTON_WIDTH = 72;
    private static final int COMMON_BUTTON_HEIGHT = 20;
    private static final int COMMON_BUTTON_GAP = 6;
    private static final int SCREEN_BACKGROUND_COLOR = 0x880E1118;
    private static final int BALANCE_HUD_X = 8;
    private static final int BALANCE_HUD_Y = 8;
    private static final int BALANCE_HUD_HEIGHT = 16;

    protected LoadingState loadingState = LoadingState.idle();
    protected ErrorState errorState = ErrorState.none();
    protected EmptyState emptyState = EmptyState.none();

    protected Button refreshButton;
    protected Button closeButton;

    protected BaseGameScreen(Component title) {
        super(title);
    }

    protected void initCommonButtons(int rightX, int topY) {
        if (refreshButton != null) {
            removeWidget(refreshButton);
            refreshButton = null;
        }

        if (closeButton != null) {
            removeWidget(closeButton);
        }

        closeButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.hub.close"),
                0,
                0,
                COMMON_BUTTON_WIDTH,
                COMMON_BUTTON_HEIGHT,
                button -> onClose()
        ));

        positionCommonButtons(rightX, topY);
    }

    protected void initRefreshButton(int rightX, int topY) {
        if (refreshButton == null) {
            refreshButton = addRenderableWidget(UiButton.create(
                    Component.translatable("screen.namanseulfarming.hub.refresh"),
                    0,
                    0,
                    COMMON_BUTTON_WIDTH,
                    COMMON_BUTTON_HEIGHT,
                    button -> onRefreshPressed()
            ));
        }
        positionCommonButtons(rightX, topY);
    }

    protected void positionCommonButtons(int rightX, int topY) {
        if (refreshButton != null) {
            refreshButton.setPosition(rightX - ((COMMON_BUTTON_WIDTH * 2) + COMMON_BUTTON_GAP + 2), topY);
            refreshButton.setWidth(COMMON_BUTTON_WIDTH);
            refreshButton.setHeight(COMMON_BUTTON_HEIGHT);
        }

        if (closeButton != null) {
            closeButton.setPosition(rightX - (COMMON_BUTTON_WIDTH + 4), topY);
            closeButton.setWidth(COMMON_BUTTON_WIDTH);
            closeButton.setHeight(COMMON_BUTTON_HEIGHT);
        }
    }

    protected void onRefreshPressed() {
        // override
    }

    protected abstract void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, SCREEN_BACKGROUND_COLOR);
        renderContents(graphics, mouseX, mouseY, partialTick);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }

        if (emptyState.active()) {
            renderEmptyState(graphics, emptyState.message());
        }

        if (errorState.hasError()) {
            renderErrorMessage(graphics, errorState.message());
        }

        if (loadingState.active()) {
            renderLoadingOverlay(graphics, loadingState.message());
        }

        renderBalanceHud(graphics);
    }

    protected void renderPanel(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight) {
        new UiPanel(x, y, panelWidth, panelHeight).render(graphics);
    }

    protected void renderSectionTitle(GuiGraphics graphics, Component title, int x, int y) {
        graphics.drawString(font, title, x, y, 0xE8F0FF, true);
    }

    protected void renderClipped(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight, Runnable drawCall) {
        if (panelWidth <= 2 || panelHeight <= 2) {
            return;
        }

        int left = Math.max(0, x + 1);
        int top = Math.max(0, y + 1);
        int right = Math.min(width, x + panelWidth - 1);
        int bottom = Math.min(height, y + panelHeight - 1);
        if (right <= left || bottom <= top) {
            return;
        }

        graphics.enableScissor(left, top, right, bottom);
        try {
            drawCall.run();
        } finally {
            graphics.disableScissor();
        }
    }

    protected void renderLoadingOverlay(GuiGraphics graphics, Component message) {
        graphics.fill(0, 0, width, height, 0xAA090A10);
        graphics.drawCenteredString(font, message, width / 2, height / 2 - 4, 0xFFFFFF);
    }

    protected void renderErrorMessage(GuiGraphics graphics, String message) {
        graphics.drawCenteredString(font, message, width / 2, 8, 0xFF7D7D);
    }

    protected void renderEmptyState(GuiGraphics graphics, Component message) {
        int y = height - 30;
        if (errorState.hasError()) {
            y -= 12;
        }
        graphics.drawCenteredString(font, message, width / 2, Math.max(8, y), 0xBFD0E8);
    }

    protected void renderBalanceHud(GuiGraphics graphics) {
        String text = BalanceHudState.labelText();
        int hudWidth = Math.max(84, font.width(text) + 10);

        int x = BALANCE_HUD_X;
        int y = BALANCE_HUD_Y;
        int right = x + hudWidth;
        int bottom = y + BALANCE_HUD_HEIGHT;

        graphics.fill(x, y, right, bottom, 0xCC121A2B);
        graphics.fill(x, y, right, y + 1, 0xFF6E86B0);
        graphics.fill(x, bottom - 1, right, bottom, 0xFF6E86B0);
        graphics.fill(x, y, x + 1, bottom, 0xFF6E86B0);
        graphics.fill(right - 1, y, right, bottom, 0xFF6E86B0);
        graphics.drawString(font, text, x + 5, y + 4, 0xE8F0FF, false);
    }

    public void setLoading(boolean loading) {
        setLoading(loading, Component.translatable("screen.namanseulfarming.hub.loading"));
    }

    public void setLoading(boolean loading, Component message) {
        this.loadingState = loading ? LoadingState.active(message) : LoadingState.idle();
    }

    public void setError(@Nullable String message) {
        this.errorState = message == null || message.isBlank() ? ErrorState.none() : new ErrorState(message);
    }

    public void setEmpty(@Nullable Component message) {
        this.emptyState = message == null ? EmptyState.none() : EmptyState.of(message);
    }

    public void setMessageBanner(@Nullable UiMessageBanner banner) {
        // no-op: shared base layout is intentionally banner-free.
    }

    protected static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
