package com.namanseul.farmingmod.client.ui.status;

import com.google.gson.JsonObject;
import java.util.List;

public record StatusOverviewData(
        JsonObject focus,
        List<JsonObject> regions,
        List<JsonObject> activeEvents,
        List<JsonObject> projectEffects,
        List<JsonObject> completedProjects,
        boolean partial,
        List<String> partialNotes,
        String generatedAt
) {
    public int regionCount() {
        return regions == null ? 0 : regions.size();
    }

    public int activeEventCount() {
        return activeEvents == null ? 0 : activeEvents.size();
    }

    public int activeProjectEffectCount() {
        return projectEffects == null ? 0 : projectEffects.size();
    }

    public int completedProjectCount() {
        return completedProjects == null ? 0 : completedProjects.size();
    }
}

