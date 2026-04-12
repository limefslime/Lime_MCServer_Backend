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
        return send(UiScreenType.HUB, action);
    }

    public static String requestShopList(boolean forceRefresh) {
        return send(UiScreenType.SHOP, UiAction.SHOP_LIST, payloadWithForceRefresh(forceRefresh));
    }

    public static String requestShopDetail(String itemId, boolean forceRefresh) {
        return send(UiScreenType.SHOP, UiAction.SHOP_DETAIL, payloadWithIdAndForceRefresh("itemId", itemId, forceRefresh));
    }

    public static String requestShopPreviewBuy(String itemId, int quantity) {
        return send(UiScreenType.SHOP, UiAction.SHOP_PREVIEW_BUY, payloadWithIdAndQuantity("itemId", itemId, quantity));
    }

    public static String requestShopPreviewSell(String itemId, int quantity) {
        return send(UiScreenType.SHOP, UiAction.SHOP_PREVIEW_SELL, payloadWithIdAndQuantity("itemId", itemId, quantity));
    }

    public static String requestShopBuy(String itemId, int quantity) {
        return send(UiScreenType.SHOP, UiAction.SHOP_BUY, payloadWithIdAndQuantity("itemId", itemId, quantity));
    }

    public static String requestShopSell(String itemId, int quantity) {
        return send(UiScreenType.SHOP, UiAction.SHOP_SELL, payloadWithIdAndQuantity("itemId", itemId, quantity));
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
        return send(UiScreenType.SHOP, UiAction.SHOP_CANCEL_SELL, payloadWithId("itemId", itemId));
    }

    public static String requestMailList(boolean forceRefresh) {
        return send(UiScreenType.MAIL, UiAction.MAIL_LIST, payloadWithForceRefresh(forceRefresh));
    }

    public static String requestMailRefresh() {
        return send(UiScreenType.MAIL, UiAction.MAIL_REFRESH, payloadWithForceRefresh(true));
    }

    public static String requestMailDetail(String mailId, boolean forceRefresh) {
        return send(UiScreenType.MAIL, UiAction.MAIL_DETAIL, payloadWithIdAndForceRefresh("mailId", mailId, forceRefresh));
    }

    public static String requestMailClaim(String mailId) {
        return send(UiScreenType.MAIL, UiAction.MAIL_CLAIM, payloadWithId("mailId", mailId));
    }

    public static String requestInvestList(boolean forceRefresh) {
        return send(UiScreenType.INVEST, UiAction.INVEST_LIST, payloadWithForceRefresh(forceRefresh));
    }

    public static String requestInvestRefresh() {
        return send(UiScreenType.INVEST, UiAction.INVEST_REFRESH, payloadWithForceRefresh(true));
    }

    public static String requestInvestDetail(String stockId, boolean forceRefresh) {
        return send(UiScreenType.INVEST, UiAction.INVEST_DETAIL, payloadWithIdAndForceRefresh("stockId", stockId, forceRefresh));
    }

    public static String requestInvestProgress(String stockId, boolean forceRefresh) {
        return send(UiScreenType.INVEST, UiAction.INVEST_PROGRESS, payloadWithIdAndForceRefresh("stockId", stockId, forceRefresh));
    }

    public static String requestInvestContribute(String stockId, int amount) {
        JsonObject payload = payloadWithId("stockId", stockId);
        payload.addProperty("amount", amount);
        return send(UiScreenType.INVEST, UiAction.INVEST_CONTRIBUTE, payload);
    }

    public static String requestInvestBuy(String stockId, int quantity) {
        return send(UiScreenType.INVEST, UiAction.INVEST_BUY, payloadWithIdAndQuantity("stockId", stockId, quantity));
    }

    public static String requestInvestSell(String stockId, int quantity) {
        return send(UiScreenType.INVEST, UiAction.INVEST_SELL, payloadWithIdAndQuantity("stockId", stockId, quantity));
    }

    public static String requestStatusOverview(boolean forceRefresh) {
        return send(UiScreenType.STATUS, UiAction.STATUS_OVERVIEW, payloadWithForceRefresh(forceRefresh));
    }

    public static String requestStatusRefresh() {
        return send(UiScreenType.STATUS, UiAction.STATUS_REFRESH, payloadWithForceRefresh(true));
    }

    private static JsonObject payloadWithForceRefresh(boolean forceRefresh) {
        JsonObject payload = new JsonObject();
        payload.addProperty("forceRefresh", forceRefresh);
        return payload;
    }

    private static JsonObject payloadWithId(String key, String value) {
        JsonObject payload = new JsonObject();
        payload.addProperty(key, value);
        return payload;
    }

    private static JsonObject payloadWithIdAndForceRefresh(String key, String value, boolean forceRefresh) {
        JsonObject payload = payloadWithId(key, value);
        payload.addProperty("forceRefresh", forceRefresh);
        return payload;
    }

    private static JsonObject payloadWithIdAndQuantity(String key, String value, int quantity) {
        JsonObject payload = payloadWithId(key, value);
        payload.addProperty("quantity", quantity);
        return payload;
    }

    private static String send(UiScreenType screenType, UiAction action) {
        return send(screenType, action, null);
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
