package com.namanseul.farmingmod.server.mail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

public final class MailClaimProtocol {
    private MailClaimProtocol() {}
    public record Outcome(boolean accepted, JsonObject result) {}
    private static final Map<String, Integer> REJECTIONS = Map.of(
            "INVALID_INPUT",400,"MAIL_NOT_FOUND",404,"MAIL_ALREADY_CLAIMED",409,"INVALID_ITEM_REWARD",409);

    public static Outcome parse(String playerId, String requestId, String mailId, int status, String body) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("invalid mail response");
        JsonObject result = parsed.getAsJsonObject();
        if (!playerId.equals(string(result,"playerId")) || !requestId.equals(string(result,"requestId"))
                || !mailId.equals(string(result,"mailId"))) throw new IllegalArgumentException("mail receipt identity mismatch");
        if (status != 200) {
            if (!Integer.valueOf(status).equals(REJECTIONS.get(string(result,"code"))) || string(result,"message").isBlank()) {
                throw new IllegalArgumentException("unconfirmed mail rejection");
            }
            return new Outcome(false,result);
        }
        if (!bool(result,"claimed")) throw new IllegalArgumentException("mail not claimed");
        bool(result,"replayed");
        int amount = integer(result,"rewardAmount");
        integer(result,"balanceAfter");
        JsonObject mail = object(result,"mail"), info = object(result,"rewardInfo");
        if (!mailId.equals(string(mail,"id")) || !playerId.equals(string(mail,"playerId"))
                || !bool(mail,"isClaimed") || amount != integer(mail,"rewardAmount")
                || amount != integer(info,"rewardAmount")) throw new IllegalArgumentException("mail reward mismatch");
        JsonElement item = info.get("itemReward");
        if (item == null) throw new IllegalArgumentException("mail item reward missing");
        if (!item.isJsonNull()) {
            if (!item.isJsonObject()) throw new IllegalArgumentException("invalid item reward");
            JsonObject reward = item.getAsJsonObject();
            if (string(reward,"itemId").isBlank() || integer(reward,"quantity") <= 0) {
                throw new IllegalArgumentException("invalid item reward");
            }
        }
        return new Outcome(true,result);
    }
    private static JsonObject object(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("missing mail object " + key);
        return value.getAsJsonObject();
    }
    private static String string(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("missing mail string " + key);
        }
        return value.getAsString();
    }
    private static boolean bool(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("missing mail flag " + key);
        }
        return value.getAsBoolean();
    }
    private static int integer(JsonObject source, String key) {
        JsonElement value = source.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("missing mail number " + key);
        }
        int number = value.getAsBigDecimal().intValueExact();
        if (number < 0) throw new IllegalArgumentException("invalid mail number " + key);
        return number;
    }
}
