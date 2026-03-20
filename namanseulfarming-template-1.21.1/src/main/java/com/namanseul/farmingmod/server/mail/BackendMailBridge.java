package com.namanseul.farmingmod.server.mail;

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

public final class BackendMailBridge {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2_000))
            .build();

    private BackendMailBridge() {}

    public static JsonElement listMailbox(String playerId) throws MailBridgeException {
        String encodedPlayerId = URLEncoder.encode(playerId, StandardCharsets.UTF_8);
        return sendGet("/mail/" + encodedPlayerId);
    }

    public static JsonElement claimMail(String mailId) throws MailBridgeException {
        String encodedMailId = URLEncoder.encode(mailId, StandardCharsets.UTF_8);
        return sendPost("/mail/" + encodedMailId + "/claim");
    }

    public static JsonElement sendItemRewardMail(
            String playerId,
            String title,
            String message,
            String itemId,
            int quantity
    ) throws MailBridgeException {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("title", title);
        body.addProperty("message", message);
        body.addProperty("rewardAmount", 0);
        JsonObject itemReward = new JsonObject();
        itemReward.addProperty("itemId", itemId);
        itemReward.addProperty("quantity", quantity);
        body.add("itemReward", itemReward);
        return sendPost("/mail/send", body);
    }

    private static JsonElement sendGet(String path) throws MailBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendPost(String path) throws MailBridgeException {
        return sendPost(path, null);
    }

    private static JsonElement sendPost(String path, JsonObject body) throws MailBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .header("Content-Type", "application/json")
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws MailBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new MailBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new MailBridgeException("mail response was empty");
            }
            return parsed;
        } catch (MailBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Mail] backend request failed: {}", ex.toString());
            }
            throw new MailBridgeException("mail backend request failed");
        }
    }

    private static URI buildUri(String path) throws MailBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new MailBridgeException("backendBaseUrl not configured");
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
        return "mail backend status=" + statusCode;
    }

    public static final class MailBridgeException extends Exception {
        public MailBridgeException(String message) {
            super(message);
        }
    }
}
