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

    public static JsonElement listProjects(UUID playerUuid, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException {
        String cacheKey = "invest:list:" + playerUuid;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendInvestBridge.listProjects();
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    public static JsonElement getProjectDetail(UUID playerUuid, String projectId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedProjectId = requireProjectId(projectId);
        String cacheKey = "invest:detail:" + playerUuid + ":" + normalizedProjectId;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement detailPayload = BackendInvestBridge.getProjectDetail(normalizedProjectId);
        JsonElement progressPayload = getProjectProgress(playerUuid, normalizedProjectId, forceRefresh);
        JsonElement completionPayload = getProjectCompletionStatus(normalizedProjectId, forceRefresh);

        JsonObject combined = new JsonObject();
        combined.addProperty("projectId", normalizedProjectId);
        combined.add("project", deepCopy(detailPayload));
        combined.add("progress", extractProgressObject(progressPayload));
        combined.add("completion", deepCopy(completionPayload));

        READ_CACHE.put(cacheKey, combined, READ_CACHE_TTL);
        return combined;
    }

    public static JsonElement getProjectProgress(UUID playerUuid, String projectId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedProjectId = requireProjectId(projectId);
        String cacheKey = "invest:progress:" + playerUuid + ":" + normalizedProjectId;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendInvestBridge.getProjectProgress(normalizedProjectId);
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    public static JsonElement invest(UUID playerUuid, String projectId, int amount)
            throws BackendInvestBridge.InvestBridgeException, InvestUiException {
        String normalizedProjectId = requireProjectId(projectId);
        if (amount <= 0) {
            throw new InvestUiException("amount must be a positive integer");
        }

        JsonElement result = BackendInvestBridge.investToProject(playerUuid.toString(), normalizedProjectId, amount);
        invalidateReadCaches();
        return result;
    }

    private static JsonElement getProjectCompletionStatus(String projectId, boolean forceRefresh)
            throws BackendInvestBridge.InvestBridgeException {
        String cacheKey = "invest:completion:" + projectId;
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(cacheKey).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendInvestBridge.getProjectCompletionStatus(projectId);
        READ_CACHE.put(cacheKey, deepCopy(result), READ_CACHE_TTL);
        return result;
    }

    private static JsonElement extractProgressObject(JsonElement progressPayload) {
        if (progressPayload != null && progressPayload.isJsonObject()) {
            JsonObject object = progressPayload.getAsJsonObject();
            JsonElement nestedProgress = object.get("progress");
            if (nestedProgress != null && nestedProgress.isJsonObject()) {
                return deepCopy(nestedProgress);
            }
        }
        return deepCopy(progressPayload);
    }

    private static String requireProjectId(String projectId) throws InvestUiException {
        if (projectId == null || projectId.isBlank()) {
            throw new InvestUiException("projectId is required");
        }
        return projectId.trim();
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
