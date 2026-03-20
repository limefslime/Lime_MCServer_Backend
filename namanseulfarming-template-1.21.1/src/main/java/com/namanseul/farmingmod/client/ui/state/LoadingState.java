package com.namanseul.farmingmod.client.ui.state;

import net.minecraft.network.chat.Component;

public record LoadingState(boolean active, Component message) {
    public static LoadingState idle() {
        return new LoadingState(false, Component.empty());
    }

    public static LoadingState active(Component message) {
        return new LoadingState(true, message);
    }
}
