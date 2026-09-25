package com.namanseul.farmingmod.client.ui.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.client.network.UiClientNetworking;
import com.namanseul.farmingmod.client.ui.shop.ShopActionPanelView;
import com.namanseul.farmingmod.client.ui.shop.ShopItemViewData;
import com.namanseul.farmingmod.client.ui.shop.ShopJsonParser;
import com.namanseul.farmingmod.client.ui.shop.ShopItemListPanel;
import com.namanseul.farmingmod.client.ui.shop.ShopPreviewViewData;
import com.namanseul.farmingmod.client.ui.shop.ShopTradeViewData;
import com.namanseul.farmingmod.client.ui.widget.BalanceHudState;
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

        itemListPanel = new ShopItemListPanel(listX + 4, listY + 18, listWidth - 8, listHeight - 22, 20);
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

        renderPanel(graphics, actionX, actionY, actionWidth, actionHeight);
        renderSectionTitle(graphics, Component.translatable("screen.namanseulfarming.shop.actions"), actionX + 6, actionY + 6);
        renderClipped(graphics, actionX, actionY, actionWidth, actionHeight, () ->
                ShopActionPanelView.render(
                        graphics,
                        font,
                        actionX + 8,
                        actionY + 20,
                        actionWidth - 16,
                        actionHeight - 24,
                        selectedItem,
                        buyPreview,
                        sellPreview,
                        lastTrade,
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

        if (itemListPanel != null) {
            int selectedBefore = itemListPanel.selectedIndex();
            if (itemListPanel.mouseClicked(mouseX, mouseY, button)) {
                if (selectedBefore != itemListPanel.selectedIndex()) {
                    onSelectedIndexChanged();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!inventoryPickerVisible
                && button == 0
                && itemListPanel != null
                && itemListPanel.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!inventoryPickerVisible
                && itemListPanel != null
                && itemListPanel.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
            if (preview.balanceAfterPreview() != null) {
                BalanceHudState.setBalance(preview.balanceAfterPreview());
            } else if (preview.balanceBefore() != null) {
                BalanceHudState.setBalance(preview.balanceBefore());
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
            if (lastTrade.balanceAfter() != null) {
                BalanceHudState.setBalance(lastTrade.balanceAfter());
            }
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
        int contentWidth = Math.max(132, actionWidth - 16);
        int controlsTop = Math.max(actionY + 88, actionY + actionHeight - 52);
        int quantityRowY = controlsTop;
        int buttonRowY = controlsTop + 24;

        int quickGap = 3;
        int quickZoneWidth = clampInt(contentWidth / 2, 72, 108);
        int quickWidth = Math.max(18, (quickZoneWidth - quickGap * 2) / 3);
        int quickStartX = contentLeft + contentWidth - quickZoneWidth;

        int inputX = contentLeft;
        int inputWidth = quickStartX - quickGap - inputX;
        if (inputWidth < 44) {
            inputWidth = 44;
        }

        quantityInput.setPosition(inputX, quantityRowY + 2);
        quantityInput.setWidth(Math.max(34, inputWidth));

        if (quantityOneButton != null) {
            quantityOneButton.setPosition(quickStartX, quantityRowY);
            quantityOneButton.setWidth(quickWidth);
        }
        if (quantityTenButton != null) {
            quantityTenButton.setPosition(quickStartX + quickWidth + quickGap, quantityRowY);
            quantityTenButton.setWidth(quickWidth);
        }
        if (quantityStackButton != null) {
            quantityStackButton.setPosition(quickStartX + (quickWidth + quickGap) * 2, quantityRowY);
            quantityStackButton.setWidth(quickWidth);
        }

        int gap = 4;
        int availableWidth = contentWidth - gap * 3;
        int buttonWidth = Math.max(28, availableWidth / 4);
        int startX = contentLeft;

        if (registerItemButton != null) {
            registerItemButton.setPosition(startX, buttonRowY);
            registerItemButton.setWidth(buttonWidth);
        }
        if (buyButton != null) {
            buyButton.setPosition(startX + (buttonWidth + gap), buttonRowY);
            buyButton.setWidth(buttonWidth);
        }
        if (sellButton != null) {
            sellButton.setPosition(startX + (buttonWidth + gap) * 2, buttonRowY);
            sellButton.setWidth(buttonWidth);
        }
        if (cancelSellButton != null) {
            cancelSellButton.setPosition(startX + (buttonWidth + gap) * 3, buttonRowY);
            cancelSellButton.setWidth(buttonWidth);
        }
    }

    private void recalcLayout() {
        frameWidth = Math.min(640, width - 20);
        frameHeight = Math.min(372, height - 30);
        frameX = (width - frameWidth) / 2;
        frameY = (height - frameHeight) / 2;

        int innerWidth = frameWidth - 20;
        int minRightWidth = 188;
        int preferredListWidth = clampInt(innerWidth * 52 / 100, 172, 332);
        if (innerWidth - preferredListWidth - 8 < minRightWidth) {
            preferredListWidth = Math.max(96, innerWidth - minRightWidth - 8);
        }

        listX = frameX + 10;
        listY = frameY + 34;
        listWidth = Math.max(96, preferredListWidth);
        actionWidth = Math.max(96, innerWidth - listWidth - 8);

        int contentTop = listY;
        int contentBottom = frameY + frameHeight - 10;
        int contentHeight = Math.max(120, contentBottom - contentTop);
        listHeight = Math.max(64, contentHeight);

        actionX = listX + listWidth + 8;
        actionY = contentTop;
        actionHeight = contentHeight;

        inventoryPickerWidth = Math.min(320, width - 40);
        inventoryPickerHeight = Math.min(190, height - 40);
        inventoryPickerX = (width - inventoryPickerWidth) / 2;
        inventoryPickerY = (height - inventoryPickerHeight) / 2;
    }

    private record InventoryChoice(int slot, ItemStack stack, String itemKey) {}
}
