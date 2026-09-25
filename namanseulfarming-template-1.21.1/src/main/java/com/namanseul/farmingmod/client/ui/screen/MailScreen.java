package com.namanseul.farmingmod.client.ui.screen;

import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.mail.MailActionPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailClaimResultViewData;
import com.namanseul.farmingmod.client.ui.mail.MailDetailPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailJsonParser;
import com.namanseul.farmingmod.client.ui.mail.MailListPanel;
import com.namanseul.farmingmod.client.ui.mail.MailRewardPanelView;
import com.namanseul.farmingmod.client.ui.mail.MailViewData;
import com.namanseul.farmingmod.client.ui.widget.BalanceHudState;
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
            if (claim.balanceAfter() != null) {
                BalanceHudState.setBalance(claim.balanceAfter());
            }
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
