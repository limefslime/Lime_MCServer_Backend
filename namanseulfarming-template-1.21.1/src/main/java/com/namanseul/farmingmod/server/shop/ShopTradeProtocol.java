package com.namanseul.farmingmod.server.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/** Pure protocol validation; no inventory mutation is permitted on an unverified response. */
public final class ShopTradeProtocol {
    private ShopTradeProtocol() {}
    public record Request(String playerId, String requestId, String transactionType, String itemId, int quantity) {}
    public record Outcome(boolean accepted, JsonObject result) {}
    private static final Map<String, Integer> REJECTIONS = Map.of(
            "INVALID_INPUT", 400, "ITEM_PRICE_NOT_TRADABLE", 400, "SELL_QUANTITY_TOO_LARGE", 400,
            "ITEM_NOT_FOUND", 404, "ITEM_INACTIVE", 409, "INSUFFICIENT_BALANCE", 409,
            "INSUFFICIENT_STOCK", 409, "TRADE_COOLDOWN_ACTIVE", 429);

    public static Outcome parse(Request request, int status, String body) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("invalid trade response");
        JsonObject result = parsed.getAsJsonObject();
        if (!request.playerId().equals(string(result, "playerId"))
                || !request.requestId().equals(string(result, "requestId"))
                || !request.transactionType().equals(string(result, "transactionType"))
                || !request.itemId().equals(string(result, "itemId"))
                || request.quantity() != integer(result, "quantity")) {
            throw new IllegalArgumentException("trade receipt does not match pending request");
        }
        if (status == 200) {
            JsonElement replayed = result.get("replayed");
            if (replayed == null || !replayed.isJsonPrimitive() || !replayed.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("trade receipt missing replay flag");
            }
            for (String field : new String[] {"balanceAfter", "totalPrice", "stockAfter", "unitPrice"}) {
                if (integer(result, field) < 0) throw new IllegalArgumentException("invalid trade amount");
            }
            return new Outcome(true, result);
        }
        String code = string(result, "code");
        if (!Integer.valueOf(status).equals(REJECTIONS.get(code)) || string(result, "message").isBlank()) {
            // Conflict, proxy errors, authentication failures and 5xx remain pending.
            throw new IllegalArgumentException("unconfirmed trade rejection");
        }
        return new Outcome(false, result);
    }

    private static String string(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing trade field " + key);
        }
        return value.getAsString();
    }
    private static int integer(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing trade number " + key);
        }
        return value.getAsBigDecimal().intValueExact();
    }
}
