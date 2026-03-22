package com.namanseul.farmingmod.server.invest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class BackendInvestBridge {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendInvestBridge() {}

    public static JsonElement listStocks(String playerId) throws InvestBridgeException {
        String encodedPlayerId = encodePathSegment(playerId);
        return sendGet("/invest/stocks?playerId=" + encodedPlayerId);
    }

    public static JsonElement getStockDetail(String stockId, String playerId) throws InvestBridgeException {
        String encodedStockId = encodePathSegment(stockId);
        String encodedPlayerId = encodePathSegment(playerId);
        return sendGet("/invest/stocks/" + encodedStockId + "?playerId=" + encodedPlayerId);
    }

    public static JsonElement buyStock(String playerId, String stockId, int quantity)
            throws InvestBridgeException {
        String encodedStockId = encodePathSegment(stockId);
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("quantity", quantity);
        return sendPost("/invest/stocks/" + encodedStockId + "/buy", body);
    }

    public static JsonElement sellStock(String playerId, String stockId, int quantity)
            throws InvestBridgeException {
        String encodedStockId = encodePathSegment(stockId);
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("quantity", quantity);
        return sendPost("/invest/stocks/" + encodedStockId + "/sell", body);
    }

    // Legacy bridge methods are kept for compatibility.
    public static JsonElement listProjects() throws InvestBridgeException {
        return sendGet("/invest/projects");
    }

    public static JsonElement getProjectDetail(String projectId) throws InvestBridgeException {
        String encodedProjectId = encodePathSegment(projectId);
        return sendGet("/invest/projects/" + encodedProjectId);
    }

    public static JsonElement getProjectProgress(String projectId) throws InvestBridgeException {
        String encodedProjectId = encodePathSegment(projectId);
        return sendGet("/invest/projects/" + encodedProjectId + "/progress");
    }

    public static JsonElement investToProject(String playerId, String projectId, int amount)
            throws InvestBridgeException {
        String encodedProjectId = encodePathSegment(projectId);
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("amount", amount);
        return sendPost("/invest/projects/" + encodedProjectId + "/invest", body);
    }

    public static JsonElement getProjectCompletionStatus(String projectId) throws InvestBridgeException {
        String encodedProjectId = encodePathSegment(projectId);
        return sendGet("/project-completion/" + encodedProjectId + "/status");
    }

    private static JsonElement sendGet(String path) throws InvestBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendPost(String path, JsonObject body) throws InvestBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws InvestBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new InvestBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new InvestBridgeException("invest response was empty");
            }
            return parsed;
        } catch (InvestBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Invest] backend request failed: {}", ex.toString());
            }
            throw new InvestBridgeException("invest backend request failed");
        }
    }

    private static URI buildUri(String path) throws InvestBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new InvestBridgeException("backendBaseUrl not configured");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String extractErrorMessage(int statusCode, String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject object = parsed.getAsJsonObject();
                JsonElement messageElement = object.get("message");
                if (messageElement != null && messageElement.isJsonPrimitive()) {
                    String message = messageElement.getAsString();
                    if (message != null && !message.isBlank()) {
                        return message;
                    }
                }
            }
        } catch (Exception ignored) {
            // keep fallback below
        }
        return "invest backend status=" + statusCode;
    }

    public static final class InvestBridgeException extends Exception {
        public InvestBridgeException(String message) {
            super(message);
        }
    }
}
