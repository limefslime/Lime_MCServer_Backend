package com.namanseul.farmingmod.network;

import com.namanseul.farmingmod.NamanseulFarming;
import com.namanseul.farmingmod.network.payload.UiResponsePayload;
import java.lang.reflect.Method;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class UiPayloadBridge {
    private UiPayloadBridge() {}

    public static void dispatchClientResponse(UiResponsePayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }

        try {
            Class<?> dispatcherClass = Class.forName("com.namanseul.farmingmod.client.network.ClientUiResponseDispatcher");
            Method handleMethod = dispatcherClass.getMethod("handle", UiResponsePayload.class, IPayloadContext.class);
            handleMethod.invoke(null, payload, context);
        } catch (Exception ex) {
            NamanseulFarming.LOGGER.warn("[UI] failed to dispatch client response handler: {}", ex.toString());
        }
    }
}
