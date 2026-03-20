package com.namanseul.farmingmod.server.summary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import java.net.ConnectException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BackendSummaryBridge {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendSummaryBridge() {}

    public static BridgeSummary fetchOpsSummary() {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            return BridgeSummary.partial("backendBaseUrl not configured");
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        URI uri = URI.create(normalizedBase + "/ops/summary");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return BridgeSummary.partial("ops summary status=" + response.statusCode());
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                return BridgeSummary.partial("ops summary payload is not JSON object");
            }
            return toBridgeSummary(parsed.getAsJsonObject());
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("Failed to fetch /ops/summary: {}", ex.toString());
            }
            return BridgeSummary.partial(classifyRequestFailure(ex));
        }
    }

    private static BridgeSummary toBridgeSummary(JsonObject root) {
        JsonObject focusSection = firstObject(root, "focus");
        JsonObject eventsSection = firstObject(root, "events");
        JsonObject projectEffectsSection = firstObject(root, "projectEffects");
        JsonObject regionsSection = firstObject(root, "regions", "regionSummary", "region");
        JsonObject investmentsSection = firstObject(root, "investments");
        JsonObject mailSection = firstObject(root, "mail");

        String focus = firstString(focusSection, "currentFocusRegion", "focusRegion");
        if (focus == null) {
            focus = firstString(root, "currentFocusRegion", "focusRegion", "focus", "regionFocus");
        }

        Integer activeEventCount = firstInt(eventsSection, "activeCount", "activeEventCount", "eventCount");
        if (activeEventCount == null) {
            activeEventCount = firstInt(root, "activeEventCount", "active_events_count", "eventCount");
        }

        Integer activeProjectEffectCount = firstInt(projectEffectsSection, "activeCount", "activeProjectEffectCount", "projectEffectCount");
        if (activeProjectEffectCount == null) {
            activeProjectEffectCount = firstInt(root, "activeProjectEffectCount", "active_project_effect_count", "projectEffectCount");
        }

        String dominantRegionCategory = firstString(regionsSection, "dominantCategory", "dominantRegionCategory", "regionCategory");
        if (dominantRegionCategory == null) {
            dominantRegionCategory = firstString(root, "dominantRegionCategory", "dominantCategory", "regionCategory");
        }

        JsonObject regionSummary = firstObject(regionsSection, "summary", "operations");
        if (dominantRegionCategory == null && regionSummary != null) {
            dominantRegionCategory = firstString(regionSummary, "dominantCategory", "category");
        }

        Integer shopPricePreview = firstInt(root, "shopPricePreview", "currentPricePreview", "pricePreview");

        Integer unclaimedMailCount = firstInt(mailSection, "unclaimedRewardCount", "unclaimedMailCount", "mailUnclaimedCount");
        if (unclaimedMailCount == null) {
            unclaimedMailCount = firstInt(root, "unclaimedMailCount", "mailUnclaimedCount");
        }

        Integer investProgressPercent = firstInt(investmentsSection, "progressPercent", "investProgressPercent", "investmentProgressPercent");
        JsonObject investSummary = firstObject(investmentsSection, "summary");
        if (investProgressPercent == null && investSummary != null) {
            investProgressPercent = firstInt(investSummary, "progressPercent", "investmentProgressPercent");
        }

        Integer regionProgressPercent = firstInt(regionsSection, "progressPercent", "regionProgressPercent", "regionProgress");
        if (regionProgressPercent == null && regionSummary != null) {
            regionProgressPercent = firstInt(regionSummary, "progressPercent", "regionProgress", "progress");
        }
        if (regionProgressPercent == null) {
            regionProgressPercent = firstInt(root, "regionProgressPercent", "regionProgress");
        }

        Long timestamp = firstTimestamp(root,
                "generatedAt", "timestamp", "updatedAt", "lastUpdatedAt");
        List<String> notes = new ArrayList<>();
        if (root.entrySet().isEmpty()) {
            notes.add("ops summary empty payload");
        }

        return new BridgeSummary(
                Optional.ofNullable(focus),
                Optional.ofNullable(activeEventCount),
                Optional.ofNullable(activeProjectEffectCount),
                Optional.ofNullable(dominantRegionCategory),
                Optional.ofNullable(shopPricePreview),
                Optional.ofNullable(unclaimedMailCount),
                Optional.ofNullable(investProgressPercent),
                Optional.ofNullable(regionProgressPercent),
                Optional.ofNullable(timestamp),
                notes
        );
    }

    private static String firstString(JsonObject root, String... keys) {
        if (root == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element != null && !element.isJsonNull()) {
                if (element.isJsonPrimitive()) {
                    return element.getAsString();
                }
            }
        }
        return null;
    }

    private static Integer firstInt(JsonObject root, String... keys) {
        if (root == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element != null && !element.isJsonNull()) {
                if (element.isJsonPrimitive()) {
                    try {
                        return element.getAsInt();
                    } catch (RuntimeException ignored) {
                        try {
                            return Math.round(element.getAsFloat());
                        } catch (RuntimeException ignoredAgain) {
                            // continue
                        }
                    }
                }
            }
        }
        return null;
    }

    private static JsonObject firstObject(JsonObject root, String... keys) {
        if (root == null) {
            return null;
        }
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private static String classifyRequestFailure(Exception ex) {
        if (hasCause(ex, HttpTimeoutException.class)
                || hasCause(ex, HttpConnectTimeoutException.class)
                || hasCause(ex, SocketTimeoutException.class)) {
            return "ops summary timeout";
        }
        if (hasCause(ex, ConnectException.class)) {
            return "ops summary connect failed";
        }
        return "ops summary request failed";
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> expectedType) {
        Throwable current = error;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Long firstTimestamp(JsonObject root, String... keys) {
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                if (element.isJsonPrimitive()) {
                    if (element.getAsJsonPrimitive().isNumber()) {
                        long raw = element.getAsLong();
                        return raw < 10_000_000_000L ? raw * 1000L : raw;
                    }
                    String text = element.getAsString();
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        try {
                            return Instant.parse(text).toEpochMilli();
                        } catch (DateTimeParseException ignoredAgain) {
                            // continue
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // continue
            }
        }
        return null;
    }

    public record BridgeSummary(
            Optional<String> focusRegion,
            Optional<Integer> activeEventCount,
            Optional<Integer> activeProjectEffectCount,
            Optional<String> dominantRegionCategory,
            Optional<Integer> shopPricePreview,
            Optional<Integer> unclaimedMailCount,
            Optional<Integer> investProgressPercent,
            Optional<Integer> regionProgressPercent,
            Optional<Long> sourceTimestampMillis,
            List<String> missingOrFallbackNotes
    ) {
        public static BridgeSummary partial(String note) {
            return new BridgeSummary(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(note)
            );
        }

        public boolean isPartial() {
            return !missingOrFallbackNotes.isEmpty();
        }

        public String compactPartialNote() {
            if (missingOrFallbackNotes.isEmpty()) {
                return "";
            }
            if (missingOrFallbackNotes.size() == 1) {
                return missingOrFallbackNotes.get(0);
            }
            return missingOrFallbackNotes.get(0) + " +" + (missingOrFallbackNotes.size() - 1);
        }
    }
}
