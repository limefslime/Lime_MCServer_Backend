package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonElement;
import com.namanseul.farmingmod.server.cache.TimedCache;
import java.time.Duration;
import java.util.UUID;

public final class ShopUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final String LIST_CACHE_KEY = "shop:list";
    private static final String DETAIL_PREFIX = "shop:detail:";
    private static final TimedCache<String, JsonElement> READ_CACHE = new TimedCache<>();

    private ShopUiService() {}

    public static JsonElement listShopItems(boolean forceRefresh) throws BackendShopBridge.ShopBridgeException {
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(LIST_CACHE_KEY).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendShopBridge.listShopItems();
        READ_CACHE.put(LIST_CACHE_KEY, result, READ_CACHE_TTL);
        return result;
    }

    public static JsonElement getShopItem(String itemId, boolean forceRefresh) throws BackendShopBridge.ShopBridgeException {
        String cacheKey = DETAIL_PREFIX + itemId;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendShopBridge.getShopItem(itemId);
        READ_CACHE.put(cacheKey, result, READ_CACHE_TTL);
        return result;
    }

    public static JsonElement previewBuy(UUID playerUuid, String itemId, int quantity)
            throws BackendShopBridge.ShopBridgeException {
        return BackendShopBridge.previewBuyItem(playerUuid.toString(), itemId, quantity);
    }

    public static JsonElement previewSell(UUID playerUuid, String itemId, int quantity)
            throws BackendShopBridge.ShopBridgeException {
        return BackendShopBridge.previewSellItem(playerUuid.toString(), itemId, quantity);
    }

    public static JsonElement buy(UUID playerUuid, String itemId, int quantity)
            throws BackendShopBridge.ShopBridgeException {
        JsonElement result = BackendShopBridge.buyItem(playerUuid.toString(), itemId, quantity);
        invalidateReadCaches();
        return result;
    }

    public static JsonElement sell(UUID playerUuid, String itemId, int quantity)
            throws BackendShopBridge.ShopBridgeException {
        JsonElement result = BackendShopBridge.sellItem(playerUuid.toString(), itemId, quantity);
        invalidateReadCaches();
        return result;
    }

    private static void invalidateReadCaches() {
        READ_CACHE.invalidateAll();
    }
}
