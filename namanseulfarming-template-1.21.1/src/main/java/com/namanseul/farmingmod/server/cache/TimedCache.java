package com.namanseul.farmingmod.server.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class TimedCache<K, T> {
    private final Map<K, CacheEntry<T>> cache = new ConcurrentHashMap<>();

    public Optional<T> get(K key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(K key, T value, Duration ttl) {
        cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
