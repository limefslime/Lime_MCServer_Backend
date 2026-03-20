package com.namanseul.farmingmod.server.summary;

import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.network.payload.HubSummaryData;
import com.namanseul.farmingmod.server.cache.TimedPlayerCache;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class HubSummaryService {
    private static final TimedPlayerCache<HubSummaryData> SUMMARY_CACHE = new TimedPlayerCache<>();

    private HubSummaryService() {}

    public static HubSummaryData getSummary(ServerPlayer player, boolean forceRefresh) {
        UUID playerId = player.getUUID();
        long now = Instant.now().toEpochMilli();
        if (!forceRefresh) {
            HubSummaryData cached = SUMMARY_CACHE.get(playerId).orElse(null);
            if (cached != null) {
                return cached.withRefreshedAt(now);
            }
        }

        BackendSummaryBridge.BridgeSummary bridge = BackendSummaryBridge.fetchOpsSummary();
        HubSummaryData fresh = buildSummary(player, bridge, now);
        SUMMARY_CACHE.put(playerId, fresh, Duration.ofSeconds(Config.summaryCacheTtlSeconds()));
        return fresh;
    }

    public static void invalidate(ServerPlayer player) {
        SUMMARY_CACHE.invalidate(player.getUUID());
    }

    private static HubSummaryData buildSummary(ServerPlayer player, BackendSummaryBridge.BridgeSummary bridge, long now) {
        String fallbackRegion = player.level().dimension().location().getPath();

        String focusRegion = bridge.focusRegion().orElse(fallbackRegion);
        int activeEventCount = bridge.activeEventCount().orElse(0);
        int activeProjectEffectCount = bridge.activeProjectEffectCount().orElse(0);
        String dominantRegionCategory = bridge.dominantRegionCategory().orElse("unknown");
        int shopPricePreview = bridge.shopPricePreview().orElse(0);
        int unclaimedMailCount = bridge.unclaimedMailCount().orElse(0);
        int investProgressPercent = clampPercent(bridge.investProgressPercent().orElse(0));
        int regionProgressPercent = clampPercent(bridge.regionProgressPercent().orElse(0));
        long generatedAt = bridge.sourceTimestampMillis().orElse(now);

        return new HubSummaryData(
                focusRegion,
                Math.max(activeEventCount, 0),
                Math.max(activeProjectEffectCount, 0),
                dominantRegionCategory,
                Math.max(shopPricePreview, 0),
                Math.max(unclaimedMailCount, 0),
                investProgressPercent,
                regionProgressPercent,
                generatedAt,
                now,
                bridge.isPartial(),
                bridge.compactPartialNote()
        );
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
