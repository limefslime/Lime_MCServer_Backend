package com.namanseul.farmingmod.client.ui.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class MailJsonParser {
    private static final long RECENT_THRESHOLD_MILLIS = 24L * 60L * 60L * 1000L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private MailJsonParser() {}

    public static List<MailViewData> parseList(String json) {
        JsonElement parsed = parseElement(json);
        JsonArray array = null;
        if (parsed.isJsonArray()) {
            array = parsed.getAsJsonArray();
        } else if (parsed.isJsonObject()) {
            JsonObject object = parsed.getAsJsonObject();
            JsonElement mails = object.get("mails");
            if (mails != null && mails.isJsonArray()) {
                array = mails.getAsJsonArray();
            } else {
                JsonElement items = object.get("items");
                if (items != null && items.isJsonArray()) {
                    array = items.getAsJsonArray();
                }
            }
        }

        if (array == null) {
            return List.of();
        }

        List<MailViewData> mails = new ArrayList<>();
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                mails.add(parseMailObject(element.getAsJsonObject()));
            }
        }
        return mails;
    }

    public static MailViewData parseDetail(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("mail detail payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        JsonObject mailObject = readObject(root, "mail");
        if (mailObject != null) {
            return parseMailObject(mailObject);
        }
        return parseMailObject(root);
    }

    public static MailClaimResultViewData parseClaim(String json) {
        JsonElement parsed = parseElement(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("mail claim payload is not an object");
        }

        JsonObject root = parsed.getAsJsonObject();
        MailViewData mail = null;
        JsonObject mailObject = readObject(root, "mail");
        if (mailObject != null) {
            mail = parseMailObject(mailObject);
        }

        JsonObject rewardInfo = readObject(root, "rewardInfo");
        if (rewardInfo == null && mailObject != null) {
            rewardInfo = readObject(mailObject, "rewardInfo");
        }

        String mailId = readString(root, "mailId", mail == null ? "" : mail.id());
        boolean claimed = readBoolean(root, "claimed", mail != null && mail.claimed());
        Integer rewardAmount = readIntNullable(root, "rewardAmount");
        if (rewardAmount == null && rewardInfo != null) {
            rewardAmount = readIntNullable(rewardInfo, "rewardAmount");
        }

        String mailType = readString(root, "mailType", mail == null ? "unknown" : mail.mailType());
        boolean hasReward = readBoolean(root, "hasReward", mail != null && mail.hasReward());
        String rewardType = readString(root, "rewardType", "");
        if (rewardType.isBlank() && rewardInfo != null) {
            rewardType = readString(rewardInfo, "rewardType", "");
        }
        if (rewardType.isBlank() && mail != null) {
            rewardType = mail.rewardType();
        }
        if (rewardType.isBlank()) {
            rewardType = "unknown";
        }

        JsonObject itemReward = rewardInfo == null ? null : readObject(rewardInfo, "itemReward");
        String itemRewardItemId = itemReward == null ? null : blankToNull(readString(itemReward, "itemId", ""));
        Integer itemRewardQuantity = itemReward == null ? null : readIntNullable(itemReward, "quantity");

        return new MailClaimResultViewData(
                mailId,
                claimed,
                rewardAmount,
                readDoubleNullable(root, "balanceAfter"),
                mailType,
                hasReward,
                rewardType,
                itemRewardItemId,
                itemRewardQuantity,
                mail
        );
    }

    private static MailViewData parseMailObject(JsonObject root) {
        JsonObject rewardInfo = readObject(root, "rewardInfo");
        JsonObject itemReward = rewardInfo == null ? null : readObject(rewardInfo, "itemReward");

        Integer rewardAmount = readIntNullable(root, "rewardAmount");
        if (rewardAmount == null && rewardInfo != null) {
            rewardAmount = readIntNullable(rewardInfo, "rewardAmount");
        }

        String rewardType = rewardInfo == null ? "" : readString(rewardInfo, "rewardType", "");
        if (rewardType.isBlank()) {
            rewardType = rewardAmount != null && rewardAmount > 0 ? "gold" : "none";
        }

        long createdAt = readTimestamp(root, "createdAt", "created_at");
        long claimedAt = readTimestamp(root, "claimedAt", "claimed_at");
        long now = Instant.now().toEpochMilli();
        boolean recent = createdAt > 0 && now - createdAt <= RECENT_THRESHOLD_MILLIS;

        String title = readString(root, "title", "(no title)");
        String message = readString(root, "message", "");
        if (message.isBlank()) {
            message = "(no message)";
        }

        boolean claimed = readBoolean(root, "claimed", false)
                || readBoolean(root, "isClaimed", false)
                || readBoolean(root, "is_claimed", false);

        return new MailViewData(
                readString(root, "id", ""),
                readString(root, "playerId", readString(root, "player_id", "")),
                title,
                message,
                readString(root, "mailType", "unknown"),
                readBoolean(root, "hasReward", rewardAmount != null && rewardAmount > 0),
                claimed,
                rewardAmount,
                rewardType,
                itemReward == null ? null : blankToNull(readString(itemReward, "itemId", "")),
                itemReward == null ? null : readIntNullable(itemReward, "quantity"),
                formatTime(createdAt),
                createdAt,
                claimedAt > 0 ? formatTime(claimedAt) : null,
                claimedAt,
                recent
        );
    }

    private static JsonElement parseElement(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty mail payload");
        }
        return JsonParser.parseString(json);
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private static long readTimestamp(JsonObject root, String... keys) {
        for (String key : keys) {
            JsonElement element = root.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                if (element.isJsonPrimitive()) {
                    if (element.getAsJsonPrimitive().isNumber()) {
                        long raw = element.getAsLong();
                        return raw < 10_000_000_000L ? raw * 1000L : raw;
                    }
                    String text = element.getAsString();
                    try {
                        long raw = Long.parseLong(text);
                        return raw < 10_000_000_000L ? raw * 1000L : raw;
                    } catch (NumberFormatException ignored) {
                        return Instant.parse(text).toEpochMilli();
                    }
                }
            } catch (Exception ignored) {
                // continue with next key
            }
        }
        return 0L;
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            String value = element.getAsString();
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Nullable
    private static Integer readIntNullable(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(element.getAsFloat());
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    @Nullable
    private static Double readDoubleNullable(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static JsonObject readObject(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    @Nullable
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
