package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.invest.InvestActionPanelView;
import com.namanseul.farmingmod.client.ui.invest.InvestInvestmentResultViewData;
import com.namanseul.farmingmod.client.ui.invest.InvestDetailPanelView;
import com.namanseul.farmingmod.client.ui.invest.InvestJsonParser;
import com.namanseul.farmingmod.client.ui.invest.InvestProgressPanelView;
import com.namanseul.farmingmod.client.ui.invest.InvestProjectListPanel;
import com.namanseul.farmingmod.client.ui.invest.InvestProjectViewData;
import com.namanseul.farmingmod.client.ui.widget.UiButton;
import com.namanseul.farmingmod.client.ui.widget.UiMessageBanner;
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
    private final List<InvestProjectViewData> projects = new ArrayList<>();

    private InvestProjectListPanel projectListPanel;
    private InvestProjectViewData selectedProject;
    private InvestInvestmentResultViewData lastInvestResult;

    private EditBox amountInput;
    private Button investButton;

    private String pendingListRequestId;
    private String pendingDetailRequestId;
    private String pendingProgressRequestId;
    private String pendingInvestRequestId;

    private boolean listLoading;
    private boolean investLoading;
    private String amountError;
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

    private int progressX;
    private int progressY;
    private int progressWidth;
    private int progressHeight;

    private int actionX;
    private int actionY;
    private int actionWidth;
    private int actionHeight;

    public InvestScreen(@Nullable Screen returnScreen) {
        super(Component.translatable("screen.namanseulfarming.invest.title"));
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
        if (closeButton != null) {
            closeButton.setMessage(Component.translatable("screen.namanseulfarming.invest.back"));
        }

        projectListPanel = new InvestProjectListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22);
        initActionWidgets();
        requestProjectList(false);
    }

    @Override
    protected void onRefreshPressed() {
        requestProjectList(true);
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
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.invest.list"), listX + 6, listY + 6);
        if (projectListPanel != null) {
            projectListPanel.render(graphics, font, mouseX, mouseY);
        }

        renderPanel(graphics, detailX, detailY, detailWidth, detailHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.invest.detail"), detailX + 6, detailY + 6);
        renderClipped(graphics, detailX, detailY, detailWidth, detailHeight, () ->
                InvestDetailPanelView.render(
                        graphics,
                        font,
                        detailX + 2,
                        detailY + 18,
                        detailWidth - 4,
                        detailHeight - 20,
                        selectedProject
                )
        );

        renderPanel(graphics, progressX, progressY, progressWidth, progressHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.invest.progress"), progressX + 6, progressY + 6);
        renderClipped(graphics, progressX, progressY, progressWidth, progressHeight, () ->
                InvestProgressPanelView.render(
                        graphics,
                        font,
                        progressX + 2,
                        progressY + 18,
                        progressWidth - 4,
                        progressHeight - 20,
                        selectedProject,
                        lastInvestResult
                )
        );

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.invest.actions"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () ->
                InvestActionPanelView.render(
                        graphics,
                        font,
                        actionX + 8,
                        actionY + 36,
                        amountError,
                        statusMessage,
                        investLoading
                )
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (projectListPanel != null && projectListPanel.mouseClicked(mouseX, mouseY, button)) {
            onSelectedIndexChanged();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (projectListPanel != null && projectListPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public void handleServerResponse(UiResponsePayload payload) {
        switch (payload.action()) {
            case INVEST_LIST, INVEST_REFRESH -> handleListResponse(payload);
            case INVEST_DETAIL -> handleDetailResponse(payload);
            case INVEST_PROGRESS -> handleProgressResponse(payload);
            case INVEST_CONTRIBUTE -> handleInvestResponse(payload);
            default -> {
                // ignore unrelated actions
            }
        }
    }

    private void initActionWidgets() {
        amountInput = addRenderableWidget(new EditBox(
                font,
                0,
                0,
                80,
                16,
                Component.translatable("screen.namanseulfarming.invest.amount")
        ));
        amountInput.setMaxLength(9);
        amountInput.setFilter(value -> value.isEmpty() || value.matches("\\d{0,9}"));
        amountInput.setValue("100");
        amountInput.setResponder(value -> onAmountInputChanged());

        investButton = addRenderableWidget(UiButton.create(
                Component.translatable("screen.namanseulfarming.invest.invest_button"),
                0,
                0,
                56,
                20,
                button -> requestInvest()
        ));
        layoutActionWidgets();
    }

    private void requestProjectList(boolean forceRefresh) {
        listLoading = true;
        setLoading(true, Component.translatable("screen.namanseulfarming.invest.loading_projects"));
        setError(null);
        statusMessage = null;
        pendingListRequestId = forceRefresh
                ? UiClientNetworking.requestInvestRefresh()
                : UiClientNetworking.requestInvestList(false);
        updateActionButtons();
    }

    private void requestProjectDetail(String projectId, boolean forceRefresh) {
        pendingDetailRequestId = UiClientNetworking.requestInvestDetail(projectId, forceRefresh);
        pendingProgressRequestId = UiClientNetworking.requestInvestProgress(projectId, forceRefresh);
    }

    private void requestInvest() {
        if (selectedProject == null) {
            showWarning("select a project first");
            return;
        }
        if (selectedProject.isCompleted()) {
            showWarning("this project is already completed");
            return;
        }

        Integer amount = validatedAmount();
        if (amount == null) {
            showWarning("amount must be a positive integer");
            return;
        }

        if (investLoading) {
            return;
        }

        investLoading = true;
        setError(null);
        statusMessage = null;
        pendingInvestRequestId = UiClientNetworking.requestInvestContribute(selectedProject.projectId(), amount);
        updateActionButtons();
    }

    private void onSelectedIndexChanged() {
        if (projectListPanel == null || projects.isEmpty()) {
            selectedProject = null;
            return;
        }

        int selectedIndex = Math.max(0, Math.min(projectListPanel.selectedIndex(), projects.size() - 1));
        selectedProject = projects.get(selectedIndex);
        statusMessage = null;
        requestProjectDetail(selectedProject.projectId(), false);
        updateActionButtons();
    }

    private void onAmountInputChanged() {
        validatedAmount();
        updateActionButtons();
    }

    @Nullable
    private Integer validatedAmount() {
        if (amountInput == null) {
            amountError = "amount input unavailable";
            return null;
        }

        String raw = amountInput.getValue();
        if (raw == null || raw.isBlank()) {
            amountError = "enter amount";
            return null;
        }

        try {
            int amount = Integer.parseInt(raw);
            if (amount <= 0) {
                amountError = "amount must be >= 1";
                return null;
            }
            amountError = null;
            return amount;
        } catch (NumberFormatException ex) {
            amountError = "amount must be numeric";
            return null;
        }
    }

    private void handleListResponse(UiResponsePayload payload) {
        if (pendingListRequestId != null && !pendingListRequestId.equals(payload.requestId())) {
            return;
        }
        pendingListRequestId = null;
        listLoading = false;
        setLoading(false);

        if (!payload.success()) {
            projects.clear();
            selectedProject = null;
            setError(payload.error() == null ? "project list request failed" : payload.error());
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.invest.banner.list_failed")));
            updateListEntries();
            updateActionButtons();
            return;
        }

        try {
            String previousProjectId = selectedProject == null ? null : selectedProject.projectId();
            List<InvestProjectViewData> fetched = InvestJsonParser.parseProjects(payload.dataJson());
            projects.clear();
            projects.addAll(fetched);
            setError(null);
            updateListEntries();
            restoreOrSelectFirst(previousProjectId);
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.INFO,
                    Component.translatable("screen.namanseulfarming.invest.banner.list_loaded")));
        } catch (Exception ex) {
            projects.clear();
            selectedProject = null;
            updateListEntries();
            setError("failed to parse invest list response");
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.translatable("screen.namanseulfarming.invest.banner.list_failed")));
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
                    Component.literal(payload.error() == null ? "project detail request failed" : payload.error())));
            return;
        }

        try {
            InvestProjectViewData detail = InvestJsonParser.parseProjectDetail(payload.dataJson());
            upsertProject(detail);
            selectedProject = detail;
            updateListEntries();
            updateSelectionByProjectId(detail.projectId());
        } catch (Exception ex) {
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING,
                    Component.literal("failed to parse project detail")));
        }
    }

    private void handleProgressResponse(UiResponsePayload payload) {
        if (pendingProgressRequestId != null && !pendingProgressRequestId.equals(payload.requestId())) {
            return;
        }
        pendingProgressRequestId = null;

        if (!payload.success()) {
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING,
                    Component.literal(payload.error() == null ? "project progress request failed" : payload.error())));
            return;
        }

        if (selectedProject == null) {
            return;
        }

        try {
            InvestProjectViewData merged = InvestJsonParser.applyProgress(
                    selectedProject.projectId(),
                    selectedProject,
                    payload.dataJson()
            );
            upsertProject(merged);
            selectedProject = merged;
            updateListEntries();
            updateSelectionByProjectId(merged.projectId());
        } catch (Exception ex) {
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING,
                    Component.literal("failed to parse project progress")));
        }
    }

    private void handleInvestResponse(UiResponsePayload payload) {
        if (pendingInvestRequestId != null && !pendingInvestRequestId.equals(payload.requestId())) {
            return;
        }
        pendingInvestRequestId = null;
        investLoading = false;

        if (!payload.success()) {
            setError(payload.error() == null ? "invest failed" : payload.error());
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.literal(payload.error() == null ? "Invest failed." : payload.error())));
            updateActionButtons();
            return;
        }

        try {
            InvestInvestmentResultViewData result = InvestJsonParser.parseInvestmentResult(payload.dataJson());
            lastInvestResult = result;

            if (selectedProject != null && selectedProject.projectId().equals(result.projectId())) {
                InvestProjectViewData merged = InvestJsonParser.applyInvestmentResult(selectedProject, result);
                upsertProject(merged);
                selectedProject = merged;
            }

            String investedText = result.investedAmount() == null ? "-" : Integer.toString(result.investedAmount());
            statusMessage = "invest success (+" + investedText + ")";
            setError(null);
            setMessageBanner(new UiMessageBanner(
                    UiMessageBanner.MessageType.INFO,
                    Component.translatable("screen.namanseulfarming.invest.banner.invest_success", investedText)
            ));

            requestProjectList(true);
            if (result.projectId() != null && !result.projectId().isBlank()) {
                requestProjectDetail(result.projectId(), true);
            }
        } catch (Exception ex) {
            setError("failed to parse invest response");
            setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.ERROR,
                    Component.literal("Failed to parse invest result")));
        }
        updateActionButtons();
    }

    private void updateListEntries() {
        if (projectListPanel == null) {
            return;
        }
        projectListPanel.setEntries(projects);
        if (projects.isEmpty()) {
            setEmpty(Component.translatable("screen.namanseulfarming.invest.no_projects"));
        } else {
            setEmpty(null);
        }
    }

    private void restoreOrSelectFirst(@Nullable String previousProjectId) {
        if (projectListPanel == null || projects.isEmpty()) {
            selectedProject = null;
            return;
        }

        int selectedIndex = 0;
        if (previousProjectId != null) {
            for (int i = 0; i < projects.size(); i++) {
                if (previousProjectId.equals(projects.get(i).projectId())) {
                    selectedIndex = i;
                    break;
                }
            }
        }

        projectListPanel.setSelectedIndex(selectedIndex);
        selectedProject = projects.get(selectedIndex);
        requestProjectDetail(selectedProject.projectId(), false);
    }

    private void updateSelectionByProjectId(String projectId) {
        if (projectListPanel == null || projectId == null) {
            return;
        }
        for (int i = 0; i < projects.size(); i++) {
            if (projectId.equals(projects.get(i).projectId())) {
                projectListPanel.setSelectedIndex(i);
                return;
            }
        }
    }

    private void upsertProject(InvestProjectViewData project) {
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).projectId().equals(project.projectId())) {
                projects.set(i, project);
                return;
            }
        }
        projects.add(project);
    }

    private void showWarning(String message) {
        setMessageBanner(new UiMessageBanner(UiMessageBanner.MessageType.WARNING, Component.literal(message)));
    }

    private void updateActionButtons() {
        boolean busy = listLoading || investLoading;
        boolean hasProject = selectedProject != null;
        boolean validAmount = validatedAmount() != null;
        boolean completed = selectedProject != null && selectedProject.isCompleted();

        if (investButton != null) {
            investButton.active = hasProject && validAmount && !busy && !completed;
        }
        if (refreshButton != null) {
            refreshButton.active = !busy;
        }
        if (closeButton != null) {
            closeButton.active = !investLoading;
        }
    }

    private void layoutActionWidgets() {
        if (amountInput == null || investButton == null) {
            return;
        }
        int buttonX = actionX + 8;
        int buttonY = actionY + 16;
        int buttonWidth = Math.max(46, Math.min(78, actionWidth / 3));
        int inputX = buttonX + buttonWidth + 6;
        int inputWidth = Math.max(38, actionX + actionWidth - 8 - inputX);

        investButton.setPosition(buttonX, buttonY);
        investButton.setWidth(buttonWidth);
        amountInput.setPosition(inputX, buttonY + 2);
        amountInput.setWidth(inputWidth);
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
        progressWidth = detailWidth;

        int gap = 6;
        int minDetail = 52;
        int minProgress = 48;
        int minAction = 64;
        actionHeight = clampInt(rightAvailableHeight / 3, minAction, 84);
        progressHeight = clampInt(rightAvailableHeight / 3, minProgress, 104);
        int detailCandidate = rightAvailableHeight - actionHeight - progressHeight - gap * 2;
        if (detailCandidate < minDetail) {
            int deficit = minDetail - detailCandidate;
            int reduceProgress = Math.min(deficit, progressHeight - minProgress);
            progressHeight -= reduceProgress;
            deficit -= reduceProgress;

            int reduceAction = Math.min(deficit, actionHeight - minAction);
            actionHeight -= reduceAction;
            deficit -= reduceAction;
        }
        detailHeight = Math.max(40, rightAvailableHeight - actionHeight - progressHeight - gap * 2);

        progressX = detailX;
        progressY = detailY + detailHeight + gap;

        actionX = detailX;
        actionY = progressY + progressHeight + gap;
        if (actionY + actionHeight > contentBottom) {
            actionHeight = Math.max(48, contentBottom - actionY);
        }
    }
}
