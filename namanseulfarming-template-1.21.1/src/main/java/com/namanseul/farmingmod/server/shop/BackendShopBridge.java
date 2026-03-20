package com.namanseul.farmingmod.server.shop;

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

public final class BackendShopBridge {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700))
            .build();

    private BackendShopBridge() {}

    public static JsonElement listShopItems() throws ShopBridgeException {
        return sendGet("/shop/items");
    }

    public static JsonElement getShopItem(String itemId) throws ShopBridgeException {
        String encodedItemId = URLEncoder.encode(itemId, StandardCharsets.UTF_8);
        return sendGet("/shop/items/" + encodedItemId);
    }

    public static JsonElement previewBuyItem(String playerId, String itemId, int quantity) throws ShopBridgeException {
        return sendPost("/shop/buy/preview", playerId, itemId, quantity);
    }

    public static JsonElement previewSellItem(String playerId, String itemId, int quantity) throws ShopBridgeException {
        return sendPost("/shop/sell/preview", playerId, itemId, quantity);
    }

    public static JsonElement buyItem(String playerId, String itemId, int quantity) throws ShopBridgeException {
        return sendPost("/shop/buy", playerId, itemId, quantity);
    }

    public static JsonElement sellItem(String playerId, String itemId, int quantity) throws ShopBridgeException {
        return sendPost("/shop/sell", playerId, itemId, quantity);
    }

    private static JsonElement sendGet(String path) throws ShopBridgeException {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .GET()
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();
        return sendAndParse(request);
    }

    private static JsonElement sendPost(String path, String playerId, String itemId, int quantity) throws ShopBridgeException {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("itemId", itemId);
        body.addProperty("quantity", quantity);

        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .timeout(Duration.ofMillis(Config.backendTimeoutMs()))
                .build();

        return sendAndParse(request);
    }

    private static JsonElement sendAndParse(HttpRequest request) throws ShopBridgeException {
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() == null ? "" : response.body();

            if (statusCode / 100 != 2) {
                throw new ShopBridgeException(extractErrorMessage(statusCode, body));
            }

            JsonElement parsed = JsonParser.parseString(body);
            if (parsed == null || parsed.isJsonNull()) {
                throw new ShopBridgeException("shop response was empty");
            }
            return parsed;
        } catch (ShopBridgeException ex) {
            throw ex;
        } catch (Exception ex) {
            if (Config.networkDebugLog()) {
                NamanseulFarming.LOGGER.warn("[UI][Shop] backend request failed: {}", ex.toString());
            }
            throw new ShopBridgeException("shop backend request failed");
        }
    }

    private static URI buildUri(String path) throws ShopBridgeException {
        String baseUrl = Config.backendBaseUrl();
        if (baseUrl.isBlank()) {
            throw new ShopBridgeException("backendBaseUrl not configured");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
    }

    private static String extractErrorMessage(int statusCode, String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed != null && parsed.isJsonObject()) {
                JsonElement messageElement = parsed.getAsJsonObject().get("message");
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
        return "shop backend status=" + statusCode;
    }

    public static final class ShopBridgeException extends Exception {
        public ShopBridgeException(String message) {
            super(message);
        }
    }
}
