package com.namanseul.farmingmod.server.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TimedPlayerCache<T> {
    private final Map<UUID, CacheEntry<T>> cache = new ConcurrentHashMap<>();

    public Optional<T> get(UUID playerId) {
        CacheEntry<T> entry = cache.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(playerId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(UUID playerId, T value, Duration ttl) {
        cache.put(playerId, new CacheEntry<>(value, Instant.now().plus(ttl)));
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
