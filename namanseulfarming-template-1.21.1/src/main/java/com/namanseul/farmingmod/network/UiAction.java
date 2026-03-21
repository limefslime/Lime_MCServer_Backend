package com.namanseul.farmingmod.network;

public enum UiAction {
    OPEN,
    INIT,
    SUMMARY,
    REFRESH,
    SHOP_LIST,
    SHOP_DETAIL,
    SHOP_PREVIEW_BUY,
    SHOP_PREVIEW_SELL,
    SHOP_BUY,
    SHOP_SELL,
    SHOP_REGISTER,
    SHOP_CANCEL_SELL,
    INVEST_LIST,
    INVEST_DETAIL,
    INVEST_PROGRESS,
    INVEST_CONTRIBUTE,
    INVEST_REFRESH,
    VILLAGE_OVERVIEW,
    VILLAGE_DONATE,
    VILLAGE_REFRESH,
    STATUS_OVERVIEW,
    STATUS_REFRESH,
    PLAYER_OVERVIEW,
    PLAYER_WALLET,
    PLAYER_ACTIVITY,
    PLAYER_SUMMARY,
    PLAYER_REFRESH,
    MAIL_LIST,
    MAIL_DETAIL,
    MAIL_CLAIM,
    MAIL_REFRESH;

    public String serialized() {
        return name().toLowerCase();
    }

    public static UiAction fromSerialized(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUMMARY;
        }

        for (UiAction action : values()) {
            if (action.serialized().equalsIgnoreCase(raw)) {
                return action;
            }
        }
        return SUMMARY;
    }
}
