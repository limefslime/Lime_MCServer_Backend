package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public record InvestProjectViewData(
        String projectId,
        String name,
        String status,
        @Nullable String description,
        @Nullable Integer currentAmount,
        @Nullable Integer targetAmount,
        @Nullable Integer remainingAmount,
        @Nullable Double progressPercent,
        @Nullable Double contributionAmount,
        @Nullable Integer contributors,
        @Nullable JsonObject completion
) {
    public String listLabel() {
        String safeName = name == null || name.isBlank() ? projectId : name;
        return safeName + " [" + safeStatus() + "]";
    }

    public String safeStatus() {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return status;
    }

    public boolean isCompleted() {
        if ("completed".equalsIgnoreCase(safeStatus())) {
            return true;
        }
        if (completion == null) {
            return false;
        }
        if (readBoolean(completion, "wasAlreadyCompleted", false)) {
            return true;
        }
        if (readBoolean(completion, "reachedTarget", false) && readBoolean(completion, "completionProcessed", false)) {
            return true;
        }
        return readBoolean(completion, "isCompleted", false);
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        if (root == null) {
            return fallback;
        }
        var element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
