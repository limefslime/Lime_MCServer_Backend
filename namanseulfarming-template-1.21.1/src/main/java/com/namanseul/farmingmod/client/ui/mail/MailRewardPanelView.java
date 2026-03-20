package com.namanseul.farmingmod.client.ui.mail;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailRewardPanelView {
    private MailRewardPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable MailViewData selectedMail,
            @Nullable MailClaimResultViewData lastClaim
    ) {
        List<Component> lines = buildLines(selectedMail, lastClaim);
        int lineY = y + 4;
        int maxY = y + height - 10;
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line, x + 6, lineY, 0xEAF1FF, false);
            lineY += 12;
        }
    }

    private static List<Component> buildLines(
            @Nullable MailViewData selectedMail,
            @Nullable MailClaimResultViewData lastClaim
    ) {
        List<Component> lines = new ArrayList<>();
        if (selectedMail == null) {
            lines.add(Component.translatable("screen.namanseulfarming.mail.no_selection"));
            return lines;
        }

        lines.add(Component.literal("reward state: " + (selectedMail.hasReward() ? "claimable" : "notification")));
        lines.add(Component.literal("rewardType: " + safe(selectedMail.rewardType())));
        lines.add(Component.literal("rewardAmount: " + numberOrDash(selectedMail.rewardAmount())));
        if (selectedMail.itemRewardItemId() != null) {
            lines.add(Component.literal("itemReward: " + selectedMail.itemRewardItemId()
                    + " x" + numberOrDash(selectedMail.itemRewardQuantity())
                    + " (server-defined)"));
        } else {
            lines.add(Component.literal("itemReward: -"));
        }

        if (lastClaim != null) {
            lines.add(Component.literal("last claim: " + (lastClaim.claimed() ? "success" : "failed")));
            lines.add(Component.literal("last rewardAmount: " + numberOrDash(lastClaim.rewardAmount())));
            lines.add(Component.literal("balanceAfter: " + numberOrDash(lastClaim.balanceAfter())));
        }

        return lines;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String numberOrDash(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }

    private static String numberOrDash(@Nullable Double value) {
        if (value == null) {
            return "-";
        }
        return String.format("%.0f", value);
    }
}
