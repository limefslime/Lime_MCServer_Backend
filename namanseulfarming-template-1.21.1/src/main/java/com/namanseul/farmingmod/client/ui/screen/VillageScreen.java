package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.village.VillageFundViewData;
import com.namanseul.farmingmod.client.ui.village.VillageJsonParser;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class VillageScreen extends BaseGameScreen {
    private final Screen returnScreen;

    private VillageFundViewData fundData;
    private EditBox donateInput;
    private Button donateButton;

    private String pendingOverviewRequestId;
    private String pendingDonateRequestId;
    private boolean overviewLoading;
    private boolean donateLoading;
    private String inputError;
    private String statusMessage;

    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;
    private int summaryX;
    private int summaryY;
    private int summaryWidth;
    private int summaryHeight;
    private int actionX;
    private int actionY;
    private int actionWidth;
    private int actionHeight;

    public VillageScreen(@Nullable Screen returnScreen) {
        super(Component.literal("Village Fund"));
        this.returnScreen = returnScreen;
    }

    public static VillageScreen openStandalone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof VillageScreen current) {
            return current;
        }

        Screen parent = minecraft.screen instanceof GameHubScreen ? minecraft.screen : new GameHubScreen();
        VillageScreen screen = new VillageScreen(parent);
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

        initActionWidgets();
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

    public void handleServerResponse(UiResponsePayload payload) {
        if (payload.action() == UiAction.VILLAGE_OVERVIEW || payload.action() == UiAction.VILLAGE_REFRESH) {
            handleOverviewResponse(payload);
            return;
        }
        if (payload.action() == UiAction.VILLAGE_DONATE) {
            handleDonateResponse(payload);
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recalcLayout();
        positionCommonButtons(frameX + frameWidth - 4, frameY + 8);
        layoutActionWidgets();
        updateActionButtons();

        renderPanel(graphics, frameX, frameY, frameWidth, frameHeight);
        renderSectionTitle(graphics, title, frameX + 10, frameY + 14);

        renderPanel(graphics, summaryX, summaryY, summaryWidth, summaryHeight);
        renderSectionTitle(graphics, Component.literal("Fund Status"), summaryX + 6, summaryY + 6);
        renderClipped(graphics, summaryX, summaryY, summaryWidth, summaryHeight, () -> renderSummary(graphics));

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.literal("Donate"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () -> renderActionPanel(graphics));
    }

    private void initActionWidgets() {
        donateInput = addRenderableWidget(new EditBox(
                font,
                0,
                0,
                120,
                16,
                Component.literal("Donation amount")
        ));
        donateInput.setMaxLength(9);
        donateInput.setFilter(value -> value.isEmpty() || value.matches("\\d{0,9}"));
        donateInput.setValue("100");
        donateInput.setResponder(value -> onInputChanged());

        donateButton = addRenderableWidget(UiButton.create(
                Component.literal("Donate"),
                0,
                0,
                90,
                20,
                button -> requestDonate()
        ));
    }

    private void recalcLayout() {
        frameWidth = Math.min(500, width - 20);
        frameHeight = Math.min(300, height - 30);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        summaryX = frameX + 10;
        summaryY = frameY + 34;
        summaryWidth = frameWidth - 20;
        summaryHeight = 132;

        actionX = frameX + 10;
        actionY = summaryY + summaryHeight + 8;
        actionWidth = frameWidth - 20;
        actionHeight = frameY + frameHeight - 10 - actionY;
    }

    private void layoutActionWidgets() {
        if (donateInput == null || donateButton == null) {
            return;
        }
        int inputX = actionX + 10;
        int inputY = actionY + 20;
        int inputWidth = Math.max(80, actionWidth - 20);
        donateInput.setPosition(inputX, inputY);
        donateInput.setWidth(inputWidth);

        donateButton.setPosition(inputX, inputY + 24);
        donateButton.setWidth(Math.max(80, Math.min(120, inputWidth)));
    }

    private void renderSummary(GuiGraphics graphics) {
        int x = summaryX + 8;
        int y = summaryY + 22;
        int gap = 12;

        if (fundData == null) {
            graphics.drawString(font, Component.literal("Loading village fund..."), x, y, 0xDDE6F9, false);
            return;
        }

        graphics.drawString(font, Component.literal("Level: " + fundData.level()), x, y, 0xFFFFFF, false);
        y += gap;
        graphics.drawString(font, Component.literal("Total Fund: " + fundData.totalAmount()), x, y, 0xEAF1FF, false);
        y += gap;
        graphics.drawString(
                font,
                Component.literal("Next Level Requirement: " + fundData.nextLevelRequirement()),
                x,
                y,
                0xEAF1FF,
                false
        );
        y += gap;
        graphics.drawString(
                font,
                Component.literal("Remaining: " + fundData.remainingToNextLevel()),
                x,
                y,
                0xEAF1FF,
                false
        );
        y += gap;
        String discountText = String.format("%.1f", fundData.shopDiscountRate() * 100.0);
        graphics.drawString(
                font,
                Component.literal("Shop Discount: " + discountText + "%"),
                x,
                y,
                0x91F7A2,
                false
        );
        y += gap;
        graphics.drawString(
                font,
                Component.literal("My Contribution: " + fundData.playerContribution()),
                x,
                y,
                0xDDE6F9,
                false
        );
        y += gap;
        graphics.drawString(font, Component.literal("Wallet: " + fundData.walletBalance()), x, y, 0xDDE6F9, false);
    }

    private void renderActionPanel(GuiGraphics graphics) {
        int x = actionX + 10;
        int y = actionY + 52;
        graphics.drawString(font, Component.literal("Donate from wallet to level up village fund."), x, y, 0xBFD0E8, false);
        y += 12;
        graphics.drawString(font, Component.literal("Effects are indirect (shop discount only)."), x, y, 0xBFD0E8, false);

        if (inputError != null && !inputError.isBlank()) {
            y += 14;
            graphics.drawString(font, Component.literal(inputError), x, y, 0xFF8E8E, false);
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            y += 14;
            graphics.drawString(font, Component.literal(statusMessage), x, y, 0xDDE6F9, false);
        }
    }

    private void requestOverview(boolean forceRefresh) {
        overviewLoading = true;
        setLoading(true, Component.literal("Loading village fund..."));
        setError(null);
        pendingOverviewRequestId = forceRefresh
                ? UiClientNetworking.requestVillageRefresh()
                : UiClientNetworking.requestVillageOverview(false);
        updateActionButtons();
    }

    private void requestDonate() {
        Integer amount = validateInput();
        if (amount == null) {
            statusMessage = "Enter a valid donation amount.";
            updateActionButtons();
            return;
        }
        donateLoading = true;
        statusMessage = null;
        pendingDonateRequestId = UiClientNetworking.requestVillageDonate(amount);
        updateActionButtons();
    }

    private void handleOverviewResponse(UiResponsePayload payload) {
        if (pendingOverviewRequestId != null && !pendingOverviewRequestId.equals(payload.requestId())) {
            return;
        }

        pendingOverviewRequestId = null;
        overviewLoading = false;
        setLoading(false);

        if (!payload.success()) {
            setError(payload.error() == null ? "Failed to load village fund." : payload.error());
            updateActionButtons();
            return;
        }

        try {
            fundData = VillageJsonParser.parseFundData(payload.dataJson());
            setError(null);
        } catch (Exception ex) {
            setError("Failed to parse village data.");
        }
        updateActionButtons();
    }

    private void handleDonateResponse(UiResponsePayload payload) {
        if (pendingDonateRequestId != null && !pendingDonateRequestId.equals(payload.requestId())) {
            return;
        }

        pendingDonateRequestId = null;
        donateLoading = false;

        if (!payload.success()) {
            setError(payload.error() == null ? "Donation failed." : payload.error());
            statusMessage = "Donation failed.";
            updateActionButtons();
            return;
        }

        try {
            fundData = VillageJsonParser.parseFundData(payload.dataJson());
            int donatedAmount = VillageJsonParser.parseDonatedAmount(payload.dataJson());
            statusMessage = "Donated " + donatedAmount + " to village fund.";
            setError(null);
        } catch (Exception ex) {
            setError("Failed to parse donation result.");
            statusMessage = "Donation result parse failed.";
        }

        requestOverview(true);
        updateActionButtons();
    }

    private void onInputChanged() {
        validateInput();
        updateActionButtons();
    }

    @Nullable
    private Integer validateInput() {
        if (donateInput == null) {
            inputError = "Input unavailable";
            return null;
        }
        String raw = donateInput.getValue();
        if (raw == null || raw.isBlank()) {
            inputError = "Enter amount";
            return null;
        }
        try {
            int amount = Integer.parseInt(raw);
            if (amount <= 0) {
                inputError = "Amount must be >= 1";
                return null;
            }
            inputError = null;
            return amount;
        } catch (NumberFormatException ex) {
            inputError = "Amount must be numeric";
            return null;
        }
    }

    private void updateActionButtons() {
        boolean busy = overviewLoading || donateLoading;
        Integer amount = validateInput();

        if (donateButton != null) {
            donateButton.active = !busy && amount != null && fundData != null;
        }
        if (refreshButton != null) {
            refreshButton.active = !busy;
        }
        if (closeButton != null) {
            closeButton.active = !donateLoading;
        }
    }
}
