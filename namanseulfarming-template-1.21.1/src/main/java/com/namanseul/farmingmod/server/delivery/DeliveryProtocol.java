package com.namanseul.farmingmod.server.delivery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.namanseul.farmingmod.server.shop.ShopTradeProtocol;
import java.util.Set;

/** A timeout, authentication error or unmatched receipt must keep inventory in escrow. */
public final class DeliveryProtocol {
    public record Request(String playerId, String requestId, String contractId, String itemId, int quantity) {}
    private DeliveryProtocol() {}
    public static ShopTradeProtocol.Outcome parse(Request request, int status, String body) {
        if (status != 200) throw new IllegalArgumentException("delivery confirmation unavailable");
        JsonObject r = JsonParser.parseString(body).getAsJsonObject();
        if (!request.playerId().equals(text(r,"playerId")) || !request.requestId().equals(text(r,"requestId"))
                || !request.contractId().equals(text(r,"contractId")) || !request.itemId().equals(text(r,"itemId"))
                || !"submit".equals(text(r,"operation")) || number(r,"quantity") != request.quantity()
                || !r.get("accepted").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("unmatched delivery receipt");
        boolean accepted = r.get("accepted").getAsBoolean();
        if (accepted) {
            JsonObject c = r.getAsJsonObject("contract");
            int required = number(c,"quantity"), delivered = number(c,"delivered");
            boolean complete = delivered == required;
            if (!request.contractId().equals(text(c,"id")) || !request.itemId().equals(text(c,"itemId"))
                    || required < 1 || required > 1000 || delivered < request.quantity() || delivered > required
                    || !(complete ? "completed" : "active").equals(text(c,"status"))
                    || number(c,"reward") < 1 || number(r,"reward") != (complete ? number(c,"reward") : 0)
                    || (complete && number(r,"balance") < 0)) throw new IllegalArgumentException("invalid delivery result");
        } else if (!Set.of("NOT_FOUND","COMPLETED","MISMATCH","BALANCE_LIMIT").contains(text(r,"code"))
                || text(r,"message").isBlank()) throw new IllegalArgumentException("unconfirmed delivery rejection");
        return new ShopTradeProtocol.Outcome(accepted,r);
    }
    private static String text(JsonObject r,String key) {
        if (!r.has(key) || !r.get(key).isJsonPrimitive() || !r.get(key).getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("invalid delivery field "+key);
        return r.get(key).getAsString();
    }
    private static int number(JsonObject r,String key) {
        if (!r.has(key) || !r.get(key).isJsonPrimitive() || !r.get(key).getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("invalid delivery number "+key);
        return r.get(key).getAsBigDecimal().intValueExact();
    }
}
