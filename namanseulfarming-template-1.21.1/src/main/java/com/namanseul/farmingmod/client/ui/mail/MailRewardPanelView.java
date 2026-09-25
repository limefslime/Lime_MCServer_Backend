package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
        int contentX = x + 6;
        int contentWidth = Math.max(0, width - 12);
        for (Component line : lines) {
            if (lineY > maxY) {
                break;
            }
            drawStructuredLine(graphics, font, line.getString(), contentX, lineY, contentWidth);
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
                    + " x" + numberOrDash(selectedMail.itemRewardQuantity())));
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

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 24) {
                int labelWidth = Math.max(52, Math.min(124, width / 2));
                UiTextRender.drawLabelValue(graphics, font, label, value, x, y, width, labelWidth, 0xC7D7F1, 0xEAF1FF);
                return;
            }
        }
        UiTextRender.drawEllipsized(graphics, font, line, x, y, width, 0xEAF1FF);
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
