package com.namanseul.farmingmod.server.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.namanseul.farmingmod.server.cache.TimedPlayerCache;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerOverviewUiService {
    private static final Duration READ_CACHE_TTL = Duration.ofSeconds(5);
    private static final TimedPlayerCache<JsonElement> WALLET_CACHE = new TimedPlayerCache<>();
    private static final TimedPlayerCache<JsonElement> MAILBOX_CACHE = new TimedPlayerCache<>();
    private static final TimedPlayerCache<JsonElement> SUMMARY_CACHE = new TimedPlayerCache<>();
    private static final TimedPlayerCache<JsonElement> OVERVIEW_CACHE = new TimedPlayerCache<>();

    private PlayerOverviewUiService() {}

    public static JsonElement getOverview(UUID playerUuid, boolean forceRefresh) {
        if (!forceRefresh) {
            JsonElement cached = OVERVIEW_CACHE.get(playerUuid).orElse(null);
            if (cached != null) {
                return deepCopy(cached);
            }
        }

        JsonArray partialNotes = new JsonArray();
        JsonObject root = new JsonObject();
        root.add("wallet", safeWallet(playerUuid, forceRefresh, partialNotes));
        root.add("activity", safeActivity(playerUuid, forceRefresh, partialNotes));
        root.add("summary", safeSummary(playerUuid, forceRefresh, partialNotes));
        root.addProperty("partial", partialNotes.size() > 0);
        root.add("partialNotes", partialNotes);
        root.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());

        OVERVIEW_CACHE.put(playerUuid, deepCopy(root), READ_CACHE_TTL);
        return root;
    }

    public static JsonElement getWallet(UUID playerUuid, boolean forceRefresh)
            throws BackendPlayerBridge.PlayerBridgeException, PlayerOverviewUiException {
        if (!forceRefresh) {
            JsonElement cached = WALLET_CACHE.get(playerUuid).orElse(null);
            if (cached != null) {
                return deepCopy(cached);
            }
        }

        JsonElement response = BackendPlayerBridge.fetchWallet(playerUuid);
        JsonObject wallet = asObject(response, "wallet response is not an object");
        if (!wallet.has("playerId")) {
            wallet.addProperty("playerId", playerUuid.toString());
        }
        WALLET_CACHE.put(playerUuid, deepCopy(wallet), READ_CACHE_TTL);
        return wallet;
    }

    public static JsonElement getActivity(UUID playerUuid, boolean forceRefresh) throws PlayerOverviewUiException {
        JsonArray partialNotes = new JsonArray();
        JsonObject activity = buildActivityPayload(playerUuid, forceRefresh, partialNotes);
        if (partialNotes.size() > 0) {
            activity.addProperty("partial", true);
            activity.add("partialNotes", partialNotes);
        }
        return activity;
    }

    public static JsonElement getSummary(UUID playerUuid, boolean forceRefresh)
            throws BackendPlayerBridge.PlayerBridgeException, PlayerOverviewUiException {
        if (!forceRefresh) {
            JsonElement cached = SUMMARY_CACHE.get(playerUuid).orElse(null);
            if (cached != null) {
                return deepCopy(cached);
            }
        }

        JsonArray partialNotes = new JsonArray();
        JsonObject summary = buildSummaryPayload(playerUuid, forceRefresh, partialNotes);
        summary.addProperty("partial", partialNotes.size() > 0);
        summary.add("partialNotes", partialNotes);
        summary.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());
        SUMMARY_CACHE.put(playerUuid, deepCopy(summary), READ_CACHE_TTL);
        return summary;
    }

    private static JsonElement safeWallet(UUID playerUuid, boolean forceRefresh, JsonArray partialNotes) {
        try {
            return getWallet(playerUuid, forceRefresh);
        } catch (Exception ex) {
            partialNotes.add("wallet: " + ex.getMessage());
            JsonObject fallback = new JsonObject();
            fallback.addProperty("playerId", playerUuid.toString());
            fallback.addProperty("balance", 0);
            return fallback;
        }
    }

    private static JsonElement safeActivity(UUID playerUuid, boolean forceRefresh, JsonArray partialNotes) {
        try {
            return getActivity(playerUuid, forceRefresh);
        } catch (Exception ex) {
            partialNotes.add("activity: " + ex.getMessage());
            JsonObject fallback = new JsonObject();
            fallback.add("items", new JsonArray());
            fallback.addProperty("count", 0);
            fallback.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());
            return fallback;
        }
    }

    private static JsonElement safeSummary(UUID playerUuid, boolean forceRefresh, JsonArray partialNotes) {
        try {
            return getSummary(playerUuid, forceRefresh);
        } catch (Exception ex) {
            partialNotes.add("summary: " + ex.getMessage());
            JsonObject fallback = new JsonObject();
            fallback.addProperty("playerId", playerUuid.toString());
            fallback.addProperty("balance", 0);
            fallback.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());
            return fallback;
        }
    }

    private static JsonObject buildActivityPayload(UUID playerUuid, boolean forceRefresh, JsonArray partialNotes)
            throws PlayerOverviewUiException {
        JsonArray trackerItems = asArray(PlayerActivityTracker.getRecent(playerUuid, 20));
        JsonArray derivedMailItems = new JsonArray();
        try {
            JsonArray mailbox = getMailboxArray(playerUuid, forceRefresh);
            derivedMailItems = deriveActivityFromMailbox(mailbox);
        } catch (Exception ex) {
            partialNotes.add("mailbox_activity: " + ex.getMessage());
        }

        List<JsonObject> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        appendActivityItems(merged, seen, trackerItems);
        appendActivityItems(merged, seen, derivedMailItems);
        merged.sort(Comparator.comparingLong(PlayerOverviewUiService::readOccurredAt).reversed());

        JsonArray items = new JsonArray();
        for (JsonObject item : merged) {
            items.add(item.deepCopy());
        }

        JsonObject payload = new JsonObject();
        payload.add("items", items);
        payload.addProperty("count", items.size());
        payload.addProperty("generatedAtEpochMillis", Instant.now().toEpochMilli());
        return payload;
    }

    private static JsonObject buildSummaryPayload(UUID playerUuid, boolean forceRefresh, JsonArray partialNotes)
            throws BackendPlayerBridge.PlayerBridgeException, PlayerOverviewUiException {
        JsonObject wallet = asObject(getWallet(playerUuid, forceRefresh), "wallet payload invalid");
        int balance = readInt(wallet, "balance", 0);

        JsonArray mailbox = new JsonArray();
        try {
            mailbox = getMailboxArray(playerUuid, forceRefresh);
        } catch (Exception ex) {
            partialNotes.add("mailbox_summary: " + ex.getMessage());
        }

        JsonObject activity = buildActivityPayload(playerUuid, forceRefresh, partialNotes);
        JsonArray activityItems = readArray(activity, "items");

        int totalMailCount = mailbox.size();
        int unclaimedRewardCount = 0;
        int claimedRewardCount = 0;
        int claimedRewardAmount = 0;
        for (JsonElement mailElement : mailbox) {
            if (mailElement == null || !mailElement.isJsonObject()) {
                continue;
            }
            JsonObject mail = mailElement.getAsJsonObject();
            boolean hasReward = readBoolean(mail, "hasReward");
            boolean isClaimed = readBoolean(mail, "isClaimed");
            int rewardAmount = readInt(mail, "rewardAmount", 0);

            if (hasReward && !isClaimed) {
                unclaimedRewardCount++;
            }
            if (hasReward && isClaimed) {
                claimedRewardCount++;
                claimedRewardAmount += Math.max(rewardAmount, 0);
            }
        }

        int shopDelta = 0;
        int investSpendTotal = 0;
        int mailRewardTotal = 0;
        for (JsonElement activityElement : activityItems) {
            if (activityElement == null || !activityElement.isJsonObject()) {
                continue;
            }
            JsonObject item = activityElement.getAsJsonObject();
            String category = readString(item, "category", "");
            int amountDelta = readInt(item, "amountDelta", 0);
            if ("shop".equalsIgnoreCase(category)) {
                shopDelta += amountDelta;
            } else if ("invest".equalsIgnoreCase(category)) {
                investSpendTotal += Math.max(0, -amountDelta);
            } else if ("mail".equalsIgnoreCase(category)) {
                mailRewardTotal += Math.max(0, amountDelta);
            }
        }

        int activeProjectCount = 0;
        try {
            JsonElement investProjects = BackendPlayerBridge.fetchInvestProjects();
            if (investProjects.isJsonArray()) {
                activeProjectCount = investProjects.getAsJsonArray().size();
            }
        } catch (Exception ex) {
            partialNotes.add("invest_summary: " + ex.getMessage());
        }

        String focusRegion = "-";
        int activeEventCount = 0;
        int activeProjectEffectCount = 0;
        String dominantRegionCategory = "-";
        try {
            JsonObject ops = asObject(BackendPlayerBridge.fetchOpsSummary(), "ops summary invalid");
            JsonObject focus = readObject(ops, "focus");
            JsonObject events = readObject(ops, "events");
            JsonObject projectEffects = readObject(ops, "projectEffects");
            JsonObject regions = readObject(ops, "regions");

            focusRegion = readString(focus, "currentFocusRegion", focusRegion);
            activeEventCount = readInt(events, "activeCount", activeEventCount);
            activeProjectEffectCount = readInt(projectEffects, "activeCount", activeProjectEffectCount);
            dominantRegionCategory = readString(regions, "dominantCategory", dominantRegionCategory);
        } catch (Exception ex) {
            partialNotes.add("ops_summary: " + ex.getMessage());
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("playerId", playerUuid.toString());
        summary.addProperty("balance", balance);

        JsonObject mailSection = new JsonObject();
        mailSection.addProperty("totalCount", totalMailCount);
        mailSection.addProperty("unclaimedRewardCount", unclaimedRewardCount);
        mailSection.addProperty("claimedRewardCount", claimedRewardCount);
        mailSection.addProperty("claimedRewardAmount", claimedRewardAmount);
        summary.add("mail", mailSection);

        JsonObject activitySection = new JsonObject();
        activitySection.addProperty("recentCount", activityItems.size());
        activitySection.addProperty("shopDelta", shopDelta);
        activitySection.addProperty("investSpendTotal", investSpendTotal);
        activitySection.addProperty("mailRewardTotal", mailRewardTotal);
        summary.add("activity", activitySection);

        JsonObject investSection = new JsonObject();
        investSection.addProperty("activeProjectCount", activeProjectCount);
        summary.add("invest", investSection);

        JsonObject statusSection = new JsonObject();
        statusSection.addProperty("currentFocusRegion", focusRegion);
        statusSection.addProperty("activeEventCount", activeEventCount);
        statusSection.addProperty("activeProjectEffectCount", activeProjectEffectCount);
        statusSection.addProperty("dominantRegionCategory", dominantRegionCategory);
        summary.add("status", statusSection);

        return summary;
    }

    private static JsonArray getMailboxArray(UUID playerUuid, boolean forceRefresh)
            throws BackendPlayerBridge.PlayerBridgeException, PlayerOverviewUiException {
        if (!forceRefresh) {
            JsonElement cached = MAILBOX_CACHE.get(playerUuid).orElse(null);
            if (cached != null) {
                return asArray(cached);
            }
        }

        JsonElement mailboxPayload = BackendPlayerBridge.fetchMailbox(playerUuid);
        JsonArray mailbox = asArray(mailboxPayload);
        MAILBOX_CACHE.put(playerUuid, deepCopy(mailbox), READ_CACHE_TTL);
        return mailbox;
    }

    private static JsonArray deriveActivityFromMailbox(JsonArray mailbox) {
        JsonArray items = new JsonArray();
        for (JsonElement mailElement : mailbox) {
            if (mailElement == null || !mailElement.isJsonObject()) {
                continue;
            }
            JsonObject mail = mailElement.getAsJsonObject();
            boolean isClaimed = readBoolean(mail, "isClaimed");
            boolean hasReward = readBoolean(mail, "hasReward");
            if (!isClaimed || !hasReward) {
                continue;
            }

            String mailId = readString(mail, "id", "unknown");
            int rewardAmount = readInt(mail, "rewardAmount", 0);
            long occurredAt = parseEpochMillis(
                    readString(mail, "claimedAt", ""),
                    parseEpochMillis(readString(mail, "createdAt", ""), 0L)
            );
            String title = readString(mail, "title", "Mail reward");

            JsonObject entry = new JsonObject();
            entry.addProperty("entryId", "mailbox:" + mailId);
            entry.addProperty("occurredAtEpochMillis", occurredAt > 0 ? occurredAt : Instant.now().toEpochMilli());
            entry.addProperty("category", "mail");
            entry.addProperty("action", "claimed");
            entry.addProperty("title", "Mail reward");
            entry.addProperty("mailId", mailId);
            entry.addProperty("amountDelta", Math.max(rewardAmount, 0));
            entry.addProperty("rewardAmount", rewardAmount);
            entry.addProperty("description", title);
            entry.addProperty("source", "mailbox");
            items.add(entry);
        }
        return items;
    }

    private static void appendActivityItems(List<JsonObject> target, Set<String> seen, JsonArray source) {
        for (JsonElement element : source) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String entryId = readString(item, "entryId", "");
            if (!entryId.isBlank()) {
                if (seen.contains(entryId)) {
                    continue;
                }
                seen.add(entryId);
            }
            target.add(item.deepCopy());
        }
    }

    private static long readOccurredAt(JsonObject item) {
        JsonElement value = item.get("occurredAtEpochMillis");
        if (value == null || value.isJsonNull()) {
            return 0L;
        }
        try {
            return value.getAsLong();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static long parseEpochMillis(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long numeric = Long.parseLong(raw);
            return numeric < 10_000_000_000L ? numeric * 1000L : numeric;
        } catch (NumberFormatException ignored) {
            try {
                return OffsetDateTime.parse(raw).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static JsonArray asArray(JsonElement payload) throws PlayerOverviewUiException {
        if (payload == null || payload.isJsonNull()) {
            return new JsonArray();
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
        throw new PlayerOverviewUiException("payload is not an array");
    }

    private static JsonObject asObject(JsonElement payload, String errorMessage) throws PlayerOverviewUiException {
        if (payload != null && payload.isJsonObject()) {
            return payload.getAsJsonObject();
        }
        throw new PlayerOverviewUiException(errorMessage);
    }

    private static JsonObject readObject(JsonObject root, String key) {
        if (root == null) {
            return null;
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static JsonArray readArray(JsonObject root, String key) {
        if (root == null) {
            return new JsonArray();
        }
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return new JsonArray();
        }
        return element.getAsJsonArray();
    }

    private static String readString(JsonObject root, String key, String fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            String parsed = value.getAsString();
            return parsed == null || parsed.isBlank() ? fallback : parsed;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject root, String key) {
        if (root == null) {
            return false;
        }
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        if (root == null) {
            return fallback;
        }
        JsonElement value = root.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(value.getAsFloat());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private static JsonElement deepCopy(JsonElement element) {
        return element == null ? new JsonObject() : element.deepCopy();
    }

    public static final class PlayerOverviewUiException extends Exception {
        public PlayerOverviewUiException(String message) {
            super(message);
        }
    }
}

