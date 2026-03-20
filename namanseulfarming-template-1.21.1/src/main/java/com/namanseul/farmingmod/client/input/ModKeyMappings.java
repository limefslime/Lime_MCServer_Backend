package com.namanseul.farmingmod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.namanseul.farmingmod.NamanseulFarming;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = NamanseulFarming.MODID, value = Dist.CLIENT)
public final class ModKeyMappings {
    public static final KeyMapping OPEN_HUB = new KeyMapping(
            "key.namanseulfarming.open_hub",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.namanseulfarming"
    );

    private ModKeyMappings() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_HUB);
    }
}
