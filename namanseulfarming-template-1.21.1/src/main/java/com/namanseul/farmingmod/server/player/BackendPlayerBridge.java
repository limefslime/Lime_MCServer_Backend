package com.namanseul.farmingmod.server.player;

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
import java.util.UUID;

public final class BackendPlayerBridge {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendPlayerBridge() {}

    public static JsonElement fetchWallet(UUID playerUuid) throws PlayerBridgeException {
        String encodedPlayerId = URLEncoder.encode(playerUuid.toString(), StandardCharsets.UTF_8);
        return sendGet("/wallet/" + encodedPlayerId);
    }

    public static JsonElement fetchMailbox(UUID playerUuid) throws PlayerBridgeException {
        String encodedPlayerId = URLEncoder.encode(playerUuid.toString(), StandardCharsets.UTF_8);
        return sendGet("/mail/" + encodedPlayerId);
    }

    public static JsonElement fetchInvestProjects() throws PlayerBridgeException {
        return sendGet("/invest/projects");
    }

    public static JsonElement fetchOpsSummary() throws PlayerBridgeException {
        return sendGet("/ops/summary");
    }

    private static JsonElement sendGet(String path) throws PlayerBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws PlayerBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new PlayerBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new PlayerBridgeException("player response was empty");
            }
            return parsed;
        } catch (PlayerBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Player] backend request failed: {}", ex.toString());
            }
            throw new PlayerBridgeException("player backend request failed");
        }
    }

    private static URI buildUri(String path) throws PlayerBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new PlayerBridgeException("backendBaseUrl not configured");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
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
        return "player backend status=" + statusCode;
    }

    public static final class PlayerBridgeException extends Exception {
        public PlayerBridgeException(String message) {
            super(message);
        }
    }
}

