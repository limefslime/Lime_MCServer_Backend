package com.namanseul.farmingmod.server.status;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.cache.TimedCache;
import java.time.Duration;
import java.time.Instant;

public final class StatusUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final TimedCache<String, JsonElement> READ_CACHE = new TimedCache<>();
    private static final String OVERVIEW_CACHE_KEY = "status:overview";

    private StatusUiService() {}

    public static JsonElement getOverview(boolean forceRefresh) throws StatusUiException {
        if (!forceRefresh) {
            JsonElement cached = READ_CACHE.get(OVERVIEW_CACHE_KEY).orElse(null);
            if (cached != null) {
                return cached.deepCopy();
            }
        }

        JsonArray partialNotes = new JsonArray();
        JsonObject root = new JsonObject();
        root.add("focus", fetchObjectSection("focus", BackendStatusBridge::fetchCurrentFocus, partialNotes));
        root.add("regions", fetchArraySection("regions", BackendStatusBridge::fetchRegions, partialNotes));
        root.add("activeEvents", fetchArraySection("events", BackendStatusBridge::fetchActiveEvents, partialNotes));
        root.add("projectEffects", fetchArraySection("projectEffects", BackendStatusBridge::fetchProjectEffects, partialNotes));
        root.add("completedProjects", fetchCompletedSection(partialNotes));
        root.addProperty("generatedAt", Instant.now().toString());
        root.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());
        root.addProperty("partial", partialNotes.size() > 0);
        root.add("partialNotes", partialNotes);

        READ_CACHE.put(OVERVIEW_CACHE_KEY, root, READ_CACHE_TTL);
        return root.deepCopy();
    }

    private static JsonElement fetchCompletedSection(JsonArray partialNotes) {
        JsonElement response = fetchSection("completedProjects", BackendStatusBridge::fetchCompletedProjects, partialNotes);
        if (response == null || response.isJsonNull()) {
            return new JsonArray();
        }
        if (response.isJsonArray()) {
            return response;
        }
        if (response.isJsonObject()) {
            JsonElement items = response.getAsJsonObject().get("items");
            if (items != null && items.isJsonArray()) {
                return items.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private static JsonObject fetchObjectSection(String sectionName, SectionLoader loader, JsonArray partialNotes) {
        JsonElement response = fetchSection(sectionName, loader, partialNotes);
        if (response != null && response.isJsonObject()) {
            return response.getAsJsonObject();
        }
        return new JsonObject();
    }

    private static JsonArray fetchArraySection(String sectionName, SectionLoader loader, JsonArray partialNotes) {
        JsonElement response = fetchSection(sectionName, loader, partialNotes);
        if (response == null || response.isJsonNull()) {
            return new JsonArray();
        }
        if (response.isJsonArray()) {
            return response.getAsJsonArray();
        }
        return new JsonArray();
    }

    private static JsonElement fetchSection(String sectionName, SectionLoader loader, JsonArray partialNotes) {
        try {
            JsonElement section = loader.load();
            return section == null ? new JsonArray() : section.deepCopy();
        } catch (BackendStatusBridge.StatusBridgeException ex) {
            partialNotes.add(sectionName + ": " + ex.getMessage());
            return new JsonArray();
        } catch (Exception ex) {
            partialNotes.add(sectionName + ": status section failed");
            return new JsonArray();
        }
    }

    @FunctionalInterface
    private interface SectionLoader {
        JsonElement load() throws Exception;
    }

    public static final class StatusUiException extends Exception {
        public StatusUiException(String message) {
            super(message);
        }
    }
}

