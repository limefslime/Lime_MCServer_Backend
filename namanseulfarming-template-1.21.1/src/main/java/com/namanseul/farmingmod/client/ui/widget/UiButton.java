package com.namanseul.farmingmod.client.ui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class UiButton {
    private UiButton() {}

    public static Button create(Component label, int x, int y, int width, int height, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(x, y, width, height).build();
    }
}
