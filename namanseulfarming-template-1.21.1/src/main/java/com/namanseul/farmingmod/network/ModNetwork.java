package com.namanseul.farmingmod.network;

import com.namanseul.farmingmod.Config;
import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.payload.UiRequestPayload;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "7";

    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                UiRequestPayload.TYPE,
                UiRequestPayload.STREAM_CODEC,
                UiServerPayloadHandlers::handleRequest
        );

        registrar.playToClient(
                UiResponsePayload.TYPE,
                UiResponsePayload.STREAM_CODEC,
                ModNetwork::handleResponseOnClient
        );

        if (Config.networkDebugLog()) {
            NamanseulFarming.LOGGER.info("[UI] Payloads registered with protocol version {}", PROTOCOL_VERSION);
        }
    }

    private static void handleResponseOnClient(UiResponsePayload payload, IPayloadContext context) {
        UiPayloadBridge.dispatchClientResponse(payload, context);
    }
}
