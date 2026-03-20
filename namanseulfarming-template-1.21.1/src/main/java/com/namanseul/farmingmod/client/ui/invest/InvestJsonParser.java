package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class InvestJsonParser {
    private InvestJsonParser() {}

    public static List<InvestProjectViewData> parseProjects(String json) {
        JsonElement parsed = parseElement(json);
        JsonArray array = null;
        if (parsed.isJsonArray()) {
            array = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            JsonObject object = parsed.getAsJsonObject();
            JsonElement projects = object.get("projects");
            if (projects != null && projects.isJsonArray()) {
                array = projects.getAsJsonArray();
            } else {
                JsonElement items = object.get("items");
                if (items != null && items.isJsonArray()) {
                    array = items.getAsJsonArray();
                }
            }
        }

        if (array == null) {
            return List.of();
        }

        List<InvestProjectViewData> projects = new ArrayList<>();
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                projects.add(parseProjectObject(element.getAsJsonObject(), null, null));
            }
        }
        return projects;
    }

    public static InvestProjectViewData parseProjectDetail(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("invest detail payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject project = readObject(root, "project");
        if (project == null) {
            project = root;
        }
        JsonObject progress = readObject(root, "progress");
        JsonObject completion = readObject(root, "completion");
        return parseProjectObject(project, progress, completion);
    }

    public static InvestProjectViewData applyProgress(String projectId, InvestProjectViewData current, String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("invest progress payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject progress = readObject(root, "progress");
        if (progress == null) {
            progress = root;
        }

        return new InvestProjectViewData(
                current.projectId(),
                current.name(),
                current.status(),
                current.description(),
                readIntNullable(progress, "currentAmount", current.currentAmount()),
                readIntNullable(progress, "targetAmount", current.targetAmount()),
                readIntNullable(progress, "remainingAmount", current.remainingAmount()),
                readDoubleNullable(progress, "progressPercent", current.progressPercent()),
                readDoubleNullable(progress, "contributionAmount", current.contributionAmount()),
                current.contributors(),
                current.completion()
        );
    }

    public static InvestInvestmentResultViewData parseInvestmentResult(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("invest result payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        return new InvestInvestmentResultViewData(
                readString(root, "projectId", ""),
                readIntNullable(root, "invested"),
                readIntNullable(root, "projectTotal"),
                readObject(root, "progress"),
                readObject(root, "completion")
        );
    }

    public static InvestProjectViewData applyInvestmentResult(
            InvestProjectViewData current,
            InvestInvestmentResultViewData result
    ) {
        JsonObject progress = result.progress();
        JsonObject completion = result.completion();
        Integer currentAmount = current.currentAmount();
        Integer targetAmount = current.targetAmount();
        Integer remainingAmount = current.remainingAmount();
        Double progressPercent = current.progressPercent();
        Double contributionAmount = current.contributionAmount();

        if (progress != null) {
            currentAmount = readIntNullable(progress, "currentAmount", currentAmount);
            targetAmount = readIntNullable(progress, "targetAmount", targetAmount);
            remainingAmount = readIntNullable(progress, "remainingAmount", remainingAmount);
            progressPercent = readDoubleNullable(progress, "progressPercent", progressPercent);
            contributionAmount = readDoubleNullable(progress, "contributionAmount", contributionAmount);
        }

        String status = current.status();
        if (completion != null && readBoolean(completion, "reachedTarget", false)) {
            status = "completed";
        }

        return new InvestProjectViewData(
                current.projectId(),
                current.name(),
                status,
                current.description(),
                currentAmount,
                targetAmount,
                remainingAmount,
                progressPercent,
                contributionAmount,
                current.contributors(),
                completion == null ? current.completion() : completion.deepCopy()
        );
    }

    private static InvestProjectViewData parseProjectObject(
            JsonObject project,
            @Nullable JsonObject progress,
            @Nullable JsonObject completion
    ) {
        Integer projectCurrent = readIntNullable(project, "currentAmount");
        Integer projectTarget = readIntNullable(project, "targetAmount");
        Double projectProgress = readDoubleNullable(project, "progress");

        Integer currentAmount = readIntNullable(progress, "currentAmount", projectCurrent);
        Integer targetAmount = readIntNullable(progress, "targetAmount", projectTarget);
        Integer remainingAmount = readIntNullable(progress, "remainingAmount");
        Double progressPercent = readDoubleNullable(progress, "progressPercent", projectProgress);
        Double contributionAmount = readDoubleNullable(progress, "contributionAmount");
        String status = readString(project, "status", "");
        if (status.isBlank()) {
            status = resolveStatusFromCompletion(completion);
        }

        return new InvestProjectViewData(
                readString(project, "id", readString(project, "projectId", "")),
                readString(project, "name", ""),
                status,
                nullable(readString(project, "description", "")),
                currentAmount,
                targetAmount,
                remainingAmount,
                progressPercent,
                contributionAmount,
                readIntNullable(project, "contributors"),
                completion == null ? null : completion.deepCopy()
        );
    }

    private static JsonElement parseElement(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty invest payload");
        }
        return JsonParser.parseString(json);
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Nullable
    private static Integer readIntNullable(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(element.getAsFloat());
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    @Nullable
    private static Integer readIntNullable(JsonObject root, String key, @Nullable Integer fallback) {
        Integer value = readIntNullable(root, key);
        return value == null ? fallback : value;
    }

    @Nullable
    private static Double readDoubleNullable(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Double readDoubleNullable(JsonObject root, String key, @Nullable Double fallback) {
        Double value = readDoubleNullable(root, key);
        return value == null ? fallback : value;
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String resolveStatusFromCompletion(@Nullable JsonObject completion) {
        if (completion == null) {
            return "active";
        }

        if (readBoolean(completion, "isCompleted", false)) {
            return "completed";
        }

        boolean reachedTarget = readBoolean(completion, "reachedTarget", false);
        boolean completionProcessed = readBoolean(completion, "completionProcessed", false);
        boolean wasAlreadyCompleted = readBoolean(completion, "wasAlreadyCompleted", false);
        if (wasAlreadyCompleted || (reachedTarget && completionProcessed)) {
            return "completed";
        }
        return "active";
    }

    @Nullable
    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable
    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
