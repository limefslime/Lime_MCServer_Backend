package com.namanseul.farmingmod.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record HubSummaryData(
        String currentFocusRegion,
        int activeEventCount,
        int activeProjectEffectCount,
        String dominantRegionCategory,
        int shopPricePreview,
        int unclaimedMailCount,
        int investProgressPercent,
        int regionProgressPercent,
        long generatedAtEpochMillis,
        long refreshedAtEpochMillis,
        boolean partial,
        String partialNote
) {
    public HubSummaryData withRefreshedAt(long refreshedAtEpochMillis) {
        return new HubSummaryData(
                currentFocusRegion,
                activeEventCount,
                activeProjectEffectCount,
                dominantRegionCategory,
                shopPricePreview,
                unclaimedMailCount,
                investProgressPercent,
                regionProgressPercent,
                generatedAtEpochMillis,
                refreshedAtEpochMillis,
                partial,
                partialNote
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HubSummaryData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HubSummaryData decode(RegistryFriendlyByteBuf buffer) {
            String currentFocusRegion = buffer.readUtf();
            int activeEventCount = buffer.readInt();
            int activeProjectEffectCount = buffer.readInt();
            String dominantRegionCategory = buffer.readUtf();
            int shopPricePreview = buffer.readInt();
            int unclaimedMailCount = buffer.readInt();
            int investProgressPercent = buffer.readInt();
            int regionProgressPercent = buffer.readInt();
            long generatedAtEpochMillis = buffer.readLong();
            long refreshedAtEpochMillis = buffer.readLong();
            boolean partial = buffer.readBoolean();

            String partialNote = null;
            if (buffer.readBoolean()) {
                partialNote = buffer.readUtf();
            }

            return new HubSummaryData(
                    currentFocusRegion,
                    activeEventCount,
                    activeProjectEffectCount,
                    dominantRegionCategory,
                    shopPricePreview,
                    unclaimedMailCount,
                    investProgressPercent,
                    regionProgressPercent,
                    generatedAtEpochMillis,
                    refreshedAtEpochMillis,
                    partial,
                    partialNote
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, HubSummaryData value) {
            buffer.writeUtf(value.currentFocusRegion);
            buffer.writeInt(value.activeEventCount);
            buffer.writeInt(value.activeProjectEffectCount);
            buffer.writeUtf(value.dominantRegionCategory);
            buffer.writeInt(value.shopPricePreview);
            buffer.writeInt(value.unclaimedMailCount);
            buffer.writeInt(value.investProgressPercent);
            buffer.writeInt(value.regionProgressPercent);
            buffer.writeLong(value.generatedAtEpochMillis);
            buffer.writeLong(value.refreshedAtEpochMillis);
            buffer.writeBoolean(value.partial);
            buffer.writeBoolean(value.partialNote != null && !value.partialNote.isBlank());
            if (value.partialNote != null && !value.partialNote.isBlank()) {
                buffer.writeUtf(value.partialNote);
            }
        }
    };
}
