package com.namanseul.farmingmod.network;

public enum UiScreenType {
    HUB,
    SHOP,
    MAIL,
    INVEST,
    VILLAGE,
    STATUS,
    PLAYER;

    public String serialized() {
        return name().toLowerCase();
    }

    public static UiScreenType fromSerialized(String raw) {
        if (raw == null || raw.isBlank()) {
            return HUB;
        }

        for (UiScreenType screenType : values()) {
            if (screenType.serialized().equalsIgnoreCase(raw)) {
                return screenType;
            }
        }
        return HUB;
    }
}
