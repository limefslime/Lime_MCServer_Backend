package com.namanseul.farmingmod.client.ui.state;

public record ErrorState(String message) {
    public static ErrorState none() {
        return new ErrorState(null);
    }

    public boolean hasError() {
        return message != null && !message.isBlank();
    }
}
