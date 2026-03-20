package com.namanseul.farmingmod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> BACKEND_BASE_URL = BUILDER
            .comment("Optional backend API base URL. Example: http://127.0.0.1:3000")
            .define("backendBaseUrl", "");

    public static final ModConfigSpec.IntValue BACKEND_TIMEOUT_MS = BUILDER
            .comment("HTTP timeout in milliseconds for backend summary bridge calls")
            .defineInRange("backendTimeoutMs", 2000, 100, 10_000);

    public static final ModConfigSpec.IntValue SUMMARY_CACHE_TTL_SECONDS = BUILDER
            .comment("Server cache ttl for read-only hub summary payloads")
            .defineInRange("summaryCacheTtlSeconds", 6, 1, 60);

    public static final ModConfigSpec.BooleanValue NETWORK_DEBUG_LOG = BUILDER
            .comment("Enable debug logs for UI packet request/response flow")
            .define("networkDebugLog", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static String backendBaseUrl() {
        String configValue = BACKEND_BASE_URL.get();
        if (configValue != null && !configValue.isBlank()) {
            return configValue.trim();
        }

        String envValue = System.getenv("NFS_BACKEND_BASE_URL");
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        return "";
    }

    public static int backendTimeoutMs() {
        return BACKEND_TIMEOUT_MS.get();
    }

    public static int summaryCacheTtlSeconds() {
        return SUMMARY_CACHE_TTL_SECONDS.get();
    }

    public static boolean networkDebugLog() {
        return NETWORK_DEBUG_LOG.get();
    }
}
