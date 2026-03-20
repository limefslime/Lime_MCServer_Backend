package com.namanseul.farmingmod.server.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.cache.TimedPlayerCache;
import java.time.Duration;
import java.util.UUID;

public final class MailUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final TimedPlayerCache<JsonElement> MAIL_LIST_CACHE = new TimedPlayerCache<>();

    private MailUiService() {}

    public static JsonElement listMailbox(UUID playerUuid, boolean forceRefresh)
            throws BackendMailBridge.MailBridgeException {
        if (!forceRefresh) {
            JsonElement cached = MAIL_LIST_CACHE.get(playerUuid).orElse(null);
            if (cached != null) {
                return cached;
            }
        }

        JsonElement result = BackendMailBridge.listMailbox(playerUuid.toString());
        MAIL_LIST_CACHE.put(playerUuid, result, READ_CACHE_TTL);
        return result;
    }

    public static JsonElement getMailDetail(UUID playerUuid, String mailId, boolean forceRefresh)
            throws BackendMailBridge.MailBridgeException, MailUiException {
        JsonArray mailbox = asMailboxArray(listMailbox(playerUuid, forceRefresh));
        for (JsonElement element : mailbox) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject mail = element.getAsJsonObject();
            String id = readString(mail, "id");
            if (mailId.equalsIgnoreCase(id)) {
                return mail;
            }
        }
        throw new MailUiException("mail not found");
    }

    public static JsonElement claim(UUID playerUuid, String mailId)
            throws BackendMailBridge.MailBridgeException, MailUiException {
        // Ensure that the requested mail belongs to the requesting player before claim.
        getMailDetail(playerUuid, mailId, true);

        JsonElement claimed = BackendMailBridge.claimMail(mailId);
        validateClaimOwner(playerUuid, claimed);
        MAIL_LIST_CACHE.invalidate(playerUuid);
        return claimed;
    }

    private static JsonArray asMailboxArray(JsonElement payload) throws MailUiException {
        if (payload == null || payload.isJsonNull()) {
            throw new MailUiException("mail list payload is empty");
        }

        if (payload.isJsonArray()) {
            return payload.getAsJsonArray();
        }

        if (payload.isJsonObject()) {
            JsonObject object = payload.getAsJsonObject();
            JsonElement mails = object.get("mails");
            if (mails != null && mails.isJsonArray()) {
                return mails.getAsJsonArray();
            }
            JsonElement items = object.get("items");
            if (items != null && items.isJsonArray()) {
                return items.getAsJsonArray();
            }
        }

        throw new MailUiException("mail list payload is invalid");
    }

    private static void validateClaimOwner(UUID playerUuid, JsonElement claimPayload) throws MailUiException {
        if (claimPayload == null || !claimPayload.isJsonObject()) {
            return;
        }

        JsonObject root = claimPayload.getAsJsonObject();
        String expectedPlayerId = playerUuid.toString();

        String directPlayerId = readString(root, "playerId");
        if (!directPlayerId.isBlank() && !expectedPlayerId.equalsIgnoreCase(directPlayerId)) {
            throw new MailUiException("mail owner mismatch");
        }

        JsonElement mailElement = root.get("mail");
        if (mailElement != null && mailElement.isJsonObject()) {
            String nestedPlayerId = readString(mailElement.getAsJsonObject(), "playerId");
            if (!nestedPlayerId.isBlank() && !expectedPlayerId.equalsIgnoreCase(nestedPlayerId)) {
                throw new MailUiException("mail owner mismatch");
            }
        }
    }

    private static String readString(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            String value = element.getAsString();
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final class MailUiException extends Exception {
        public MailUiException(String message) {
            super(message);
        }
    }
}
