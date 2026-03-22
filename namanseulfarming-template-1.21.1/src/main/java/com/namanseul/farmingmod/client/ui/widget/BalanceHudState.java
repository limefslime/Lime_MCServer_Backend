package com.namanseul.farmingmod.client.ui.widget;

import java.text.NumberFormat;
import org.jetbrains.annotations.Nullable;

public final class BalanceHudState {
    @Nullable
    private static Integer currentBalance;

    private BalanceHudState() {}

    public static void setBalance(@Nullable Integer balance) {
        if (balance == null) {
            return;
        }
        currentBalance = Math.max(0, balance);
    }

    public static void setBalance(@Nullable Double balance) {
        if (balance == null || balance.isNaN() || balance.isInfinite()) {
            return;
        }
        setBalance((int) Math.round(balance));
    }

    public static String labelText() {
        if (currentBalance == null) {
            return "Balance: -";
        }
        return "Balance: " + NumberFormat.getIntegerInstance().format(currentBalance);
    }
}
