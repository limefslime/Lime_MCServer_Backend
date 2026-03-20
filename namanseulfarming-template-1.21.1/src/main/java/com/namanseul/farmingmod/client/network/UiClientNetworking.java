package com.namanseul.farmingmod.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import com.namanseul.farmingmod.network.payload.UiRequestPayload;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;

public final class UiClientNetworking {
    private static final Gson GSON = new Gson();

    private UiClientNetworking() {}

    public static String requestHub(UiAction action) {
        return send(UiScreenType.HUB, action, null);
    }

    public static String requestShopList(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.SHOP, UiAction.SHOP_LIST, payload);
    }

    public static String requestShopDetail(String itemId, boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.SHOP, UiAction.SHOP_DETAIL, payload);
    }

    public static String requestShopPreviewBuy(String itemId, int quantity) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        return send(UiScreenType.SHOP, UiAction.SHOP_PREVIEW_BUY, payload);
    }

    public static String requestShopPreviewSell(String itemId, int quantity) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        return send(UiScreenType.SHOP, UiAction.SHOP_PREVIEW_SELL, payload);
    }

    public static String requestShopBuy(String itemId, int quantity) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        return send(UiScreenType.SHOP, UiAction.SHOP_BUY, payload);
    }

    public static String requestShopSell(String itemId, int quantity) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("quantity", quantity);
        return send(UiScreenType.SHOP, UiAction.SHOP_SELL, payload);
    }

    public static String requestShopRegister(String itemId, String itemName, int quantity, int slot) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("itemName", itemName);
        payload.addProperty("quantity", quantity);
        payload.addProperty("slot", slot);
        return send(UiScreenType.SHOP, UiAction.SHOP_REGISTER, payload);
    }

    public static String requestShopCancelSell(String itemId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        return send(UiScreenType.SHOP, UiAction.SHOP_CANCEL_SELL, payload);
    }

    public static String requestMailList(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.MAIL, UiAction.MAIL_LIST, payload);
    }

    public static String requestMailRefresh() {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", true);
        return send(UiScreenType.MAIL, UiAction.MAIL_REFRESH, payload);
    }

    public static String requestMailDetail(String mailId, boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("mailId", mailId);
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.MAIL, UiAction.MAIL_DETAIL, payload);
    }

    public static String requestMailClaim(String mailId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("mailId", mailId);
        return send(UiScreenType.MAIL, UiAction.MAIL_CLAIM, payload);
    }

    public static String requestInvestList(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.INVEST, UiAction.INVEST_LIST, payload);
    }

    public static String requestInvestRefresh() {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", true);
        return send(UiScreenType.INVEST, UiAction.INVEST_REFRESH, payload);
    }

    public static String requestInvestDetail(String projectId, boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("projectId", projectId);
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.INVEST, UiAction.INVEST_DETAIL, payload);
    }

    public static String requestInvestProgress(String projectId, boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("projectId", projectId);
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.INVEST, UiAction.INVEST_PROGRESS, payload);
    }

    public static String requestInvestContribute(String projectId, int amount) {
        JsonObject payload = new JsonObject();
        payload.addProperty("projectId", projectId);
        payload.addProperty("amount", amount);
        return send(UiScreenType.INVEST, UiAction.INVEST_CONTRIBUTE, payload);
    }

    public static String requestStatusOverview(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.STATUS, UiAction.STATUS_OVERVIEW, payload);
    }

    public static String requestStatusRefresh() {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", true);
        return send(UiScreenType.STATUS, UiAction.STATUS_REFRESH, payload);
    }

    public static String requestPlayerOverview(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.PLAYER, UiAction.PLAYER_OVERVIEW, payload);
    }

    public static String requestPlayerWallet(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.PLAYER, UiAction.PLAYER_WALLET, payload);
    }

    public static String requestPlayerActivity(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.PLAYER, UiAction.PLAYER_ACTIVITY, payload);
    }

    public static String requestPlayerSummary(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return send(UiScreenType.PLAYER, UiAction.PLAYER_SUMMARY, payload);
    }

    public static String requestPlayerRefresh() {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", true);
        return send(UiScreenType.PLAYER, UiAction.PLAYER_REFRESH, payload);
    }

    private static String send(UiScreenType screenType, UiAction action, JsonObject payloadJson) {
        String requestId = UUID.randomUUID().toString();
        String encodedPayload = payloadJson == null ? null : GSON.toJson(payloadJson);
        UiRequestPayload payload = new UiRequestPayload(requestId, screenType, action, encodedPayload);
        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info(
                    "[UI] send request C->S id={} screen={} action={}",
                    requestId,
                    screenType.serialized(),
                    action.serialized()
            );
        }
        PacketDistributor.sendToServer(payload);
        return requestId;
    }
}
