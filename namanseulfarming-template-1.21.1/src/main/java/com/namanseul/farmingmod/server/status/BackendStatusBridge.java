package com.namanseul.farmingmod.server.status;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class BackendStatusBridge {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendStatusBridge() {}

    public static JsonElement fetchCurrentFocus() throws StatusBridgeException {
        return sendGet("/focus/current");
    }

    public static JsonElement fetchRegions() throws StatusBridgeException {
        return sendGet("/regions");
    }

    public static JsonElement fetchActiveEvents() throws StatusBridgeException {
        return sendGet("/events/active");
    }

    public static JsonElement fetchProjectEffects() throws StatusBridgeException {
        return sendGet("/project-completion/effects");
    }

    public static JsonElement fetchCompletedProjects() throws StatusBridgeException {
        return sendGet("/project-completion/completed");
    }

    private static JsonElement sendGet(String path) throws StatusBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws StatusBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new StatusBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new StatusBridgeException("status response was empty");
            }
            return parsed;
        } catch (StatusBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Status] backend request failed: {}", ex.toString());
            }
            throw new StatusBridgeException("status backend request failed");
        }
    }

    private static URI buildUri(String path) throws StatusBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new StatusBridgeException("backendBaseUrl not configured");
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
        return "status backend status=" + statusCode;
    }

    public static final class StatusBridgeException extends Exception {
        public StatusBridgeException(String message) {
            super(message);
        }
    }
}

