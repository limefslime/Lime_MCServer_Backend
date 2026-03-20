package com.namanseul.farmingmod.client.ui.state;

import net.minecraft.network.chat.Component;

public record EmptyState(boolean active, Component message) {
    public static EmptyState none() {
        return new EmptyState(false, Component.empty());
    }

    public static EmptyState of(Component message) {
        return new EmptyState(true, message);
    }
}
