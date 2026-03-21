package com.namanseul.farmingmod.server.invest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.cache.TimedCache;
import java.time.Duration;
import java.util.UUID;

public final class InvestUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final TimedCache<String, JsonElement> READ_CACHE = new TimedCache<>();

    private InvestUiService() {}

    public static JsonElement listStocks(UUID playerUuid, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException {
        String cacheKey = "invest:stocks:" + playerUuid;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendInvestBridge.listStocks(playerUuid.toString());
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    public static JsonElement getStockDetail(UUID playerUuid, String stockId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedStockId = requireStockId(stockId);
        String cacheKey = "invest:detail:" + playerUuid + ":" + normalizedStockId;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendInvestBridge.getStockDetail(normalizedStockId, playerUuid.toString());
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    public static JsonElement buy(UUID playerUuid, String stockId, int quantity)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedStockId = requireStockId(stockId);
        if (quantity <= 0) {
            throw new InvestUiException("quantity must be a positive integer");
        }

        JsonElement result = BackendInvestBridge.buyStock(playerUuid.toString(), normalizedStockId, quantity);
        invalidateReadCaches();
        return result;
    }

    public static JsonElement sell(UUID playerUuid, String stockId, int quantity)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedStockId = requireStockId(stockId);
        if (quantity <= 0) {
            throw new InvestUiException("quantity must be a positive integer");
        }

        JsonElement result = BackendInvestBridge.sellStock(playerUuid.toString(), normalizedStockId, quantity);
        invalidateReadCaches();
        return result;
    }

    // Legacy methods are retained for older actions.
    public static JsonElement listProjects(UUID playerUuid, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException {
        return listStocks(playerUuid, forceRefresh);
    }

    public static JsonElement getProjectDetail(UUID playerUuid, String projectId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        return getStockDetail(playerUuid, projectId, forceRefresh);
    }

    public static JsonElement getProjectProgress(UUID playerUuid, String projectId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        return getStockDetail(playerUuid, projectId, forceRefresh);
    }

    public static JsonElement invest(UUID playerUuid, String projectId, int amount)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        return buy(playerUuid, projectId, amount);
    }

    private static String requireStockId(String stockId) throws InvestUiException {
        if (stockId == null || stockId.isBlank()) {
            throw new InvestUiException("stockId is required");
        }
        return stockId.trim();
    }

    private static JsonElement deepCopy(JsonElement element) {
        return element == null ? new JsonObject() : element.deepCopy();
    }

    private static void invalidateReadCaches() {
        READ_CACHE.invalidateAll();
    }

    public static final class InvestUiException extends Exception {
        public InvestUiException(String message) {
            super(message);
        }
    }
}
