package com.namanseul.farmingmod.network.payload;

import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.UiAction;
import com.namanseul.farmingmod.network.UiScreenType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UiResponsePayload(
        String requestId,
        boolean success,
        UiScreenType screenType,
        UiAction action,
        HubSummaryData data,
        String dataJson,
        String error
) implements CustomPacketPayload {
    public static final Type<UiResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NamanseulFarming.MODID, "ui_response")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, UiResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UiResponsePayload decode(RegistryFriendlyByteBuf buffer) {
            String requestId = buffer.readUtf();
            boolean success = buffer.readBoolean();
            UiScreenType screenType = UiScreenType.fromSerialized(buffer.readUtf());
            UiAction action = UiAction.fromSerialized(buffer.readUtf());

            HubSummaryData data = null;
            if (buffer.readBoolean()) {
                data = HubSummaryData.STREAM_CODEC.decode(buffer);
            }

            String dataJson = null;
            if (buffer.readBoolean()) {
                dataJson = buffer.readUtf();
            }

            String error = null;
            if (buffer.readBoolean()) {
                error = buffer.readUtf();
            }

            return new UiResponsePayload(requestId, success, screenType, action, data, dataJson, error);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, UiResponsePayload value) {
            buffer.writeUtf(value.requestId);
            buffer.writeBoolean(value.success);
            buffer.writeUtf(value.screenType.serialized());
            buffer.writeUtf(value.action.serialized());

            buffer.writeBoolean(value.data != null);
            if (value.data != null) {
                HubSummaryData.STREAM_CODEC.encode(buffer, value.data);
            }

            buffer.writeBoolean(value.dataJson != null && !value.dataJson.isBlank());
            if (value.dataJson != null && !value.dataJson.isBlank()) {
                buffer.writeUtf(value.dataJson);
            }

            buffer.writeBoolean(value.error != null && !value.error.isBlank());
            if (value.error != null && !value.error.isBlank()) {
                buffer.writeUtf(value.error);
            }
        }
    };

    public static UiResponsePayload openHub(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.HUB, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload openShop(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.SHOP, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload openMail(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.MAIL, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload openInvest(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.INVEST, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload openStatus(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.STATUS, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload openPlayer(String requestId) {
        return new UiResponsePayload(requestId, true, UiScreenType.PLAYER, UiAction.OPEN, null, null, null);
    }

    public static UiResponsePayload successSummary(String requestId, UiAction action, HubSummaryData data) {
        return new UiResponsePayload(requestId, true, UiScreenType.HUB, action, data, null, null);
    }

    public static UiResponsePayload successJson(
            String requestId,
            UiScreenType screenType,
            UiAction action,
            String dataJson
    ) {
        return new UiResponsePayload(requestId, true, screenType, action, null, dataJson, null);
    }

    public static UiResponsePayload failed(String requestId, UiScreenType screenType, UiAction action, String error) {
        return new UiResponsePayload(requestId, false, screenType, action, null, null, error);
    }

    @Override
    public Type<UiResponsePayload> type() {
        return TYPE;
    }
}
