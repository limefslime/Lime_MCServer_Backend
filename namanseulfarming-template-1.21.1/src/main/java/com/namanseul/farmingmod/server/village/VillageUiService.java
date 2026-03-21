package com.namanseul.farmingmod.server.village;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.cache.TimedCache;
import java.time.Duration;
import java.util.UUID;

public final class VillageUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final TimedCache<String, JsonElement> READ_CACHE = new TimedCache<>();

    private VillageUiService() {}

    public static JsonElement getOverview(UUID playerUuid, boolean forceRefresh)
            throws BackendVillageBridge.VillageBridgeException {
        String cacheKey = "village:overview:" + playerUuid;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendVillageBridge.getVillageFund(playerUuid.toString());
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    public static JsonElement donate(UUID playerUuid, int amount)
            throws BackendVillageBridge.VillageBridgeException, VillageUiException {
        if (amount <= 0) {
            throw new VillageUiException("amount must be a positive integer");
        }
        JsonElement result = BackendVillageBridge.donateVillageFund(playerUuid.toString(), amount);
        READ_CACHE.invalidateAll();
        return result;
    }

    private static JsonElement deepCopy(JsonElement element) {
        return element == null ? new JsonObject() : element.deepCopy();
    }

    public static final class VillageUiException extends Exception {
        public VillageUiException(String message) {
            super(message);
        }
    }
}
