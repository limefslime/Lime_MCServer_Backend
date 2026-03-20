package com.namanseul.farmingmod.network.payload;

import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UiRequestPayload(
        String requestId,
        UiScreenType screenType,
        UiAction action,
        String payloadJson
) implements CustomPacketPayload {
    public static final Type<UiRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NamanseulFarming.MODID, "ui_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UiRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UiRequestPayload decode(RegistryFriendlyByteBuf buffer) {
            String requestId = buffer.readUtf();
            UiScreenType screenType = UiScreenType.fromSerialized(buffer.readUtf());
            UiAction action = UiAction.fromSerialized(buffer.readUtf());
            String payloadJson = null;
            if (buffer.readBoolean()) {
                payloadJson = buffer.readUtf();
            }
            return new UiRequestPayload(requestId, screenType, action, payloadJson);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, UiRequestPayload value) {
            buffer.writeUtf(value.requestId);
            buffer.writeUtf(value.screenType.serialized());
            buffer.writeUtf(value.action.serialized());
            buffer.writeBoolean(value.payloadJson != null && !value.payloadJson.isBlank());
            if (value.payloadJson != null && !value.payloadJson.isBlank()) {
                buffer.writeUtf(value.payloadJson);
            }
        }
    };

    public UiRequestPayload(String requestId, UiScreenType screenType, UiAction action) {
        this(requestId, screenType, action, null);
    }

    @Override
    public Type<UiRequestPayload> type() {
        return TYPE;
    }
}
