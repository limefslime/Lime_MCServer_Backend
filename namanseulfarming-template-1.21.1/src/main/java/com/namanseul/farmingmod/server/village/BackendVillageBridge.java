package com.namanseul.farmingmod.server.village;

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

public final class BackendVillageBridge {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendVillageBridge() {}

    public static JsonElement getVillageFund(String playerId) throws VillageBridgeException {
        String encodedPlayerId = encodePathSegment(playerId);
        return sendGet("/village/fund?playerId=" + encodedPlayerId);
    }

    public static JsonElement donateVillageFund(String playerId, int amount) throws VillageBridgeException {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("amount", amount);
        return sendPost("/village/fund/donate", body);
    }

    private static JsonElement sendGet(String path) throws VillageBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendPost(String path, JsonObject body) throws VillageBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws VillageBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new VillageBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new VillageBridgeException("village response was empty");
            }
            return parsed;
        } catch (VillageBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Village] backend request failed: {}", ex.toString());
            }
            throw new VillageBridgeException("village backend request failed");
        }
    }

    private static URI buildUri(String path) throws VillageBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new VillageBridgeException("backendBaseUrl not configured");
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
        return "village backend status=" + statusCode;
    }

    public static final class VillageBridgeException extends Exception {
        public VillageBridgeException(String message) {
            super(message);
        }
    }
}
