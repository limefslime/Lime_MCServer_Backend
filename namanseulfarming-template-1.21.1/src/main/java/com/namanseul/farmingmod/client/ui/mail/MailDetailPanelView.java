package com.namanseul.farmingmod.client.ui.mail;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class MailDetailPanelView {
    private MailDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable MailViewData mail
    ) {
        List<Component> lines = buildLines(font, width - 12, mail);
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

    private static List<Component> buildLines(Font font, int contentWidth, @Nullable MailViewData mail) {
        List<Component> lines = new ArrayList<>();
        if (mail == null) {
            lines.add(Component.translatable("screen.namanseulfarming.mail.no_selection"));
            return lines;
        }

        lines.add(Component.literal("title: " + safe(mail.title())));
        lines.add(Component.literal("mailId: " + safe(mail.id())));
        lines.add(Component.literal("mailType: " + safe(mail.mailType())));
        lines.add(Component.literal("claimed: " + mail.claimed()));
        lines.add(Component.literal("hasReward: " + mail.hasReward()));
        lines.add(Component.literal("rewardAmount: " + numberOrDash(mail.rewardAmount())));
        lines.add(Component.literal("rewardType: " + safe(mail.rewardType())));
        lines.add(Component.literal("sentAt: " + safe(mail.createdAtText())));
        if (mail.claimedAtText() != null && !mail.claimedAtText().isBlank()) {
            lines.add(Component.literal("claimedAt: " + mail.claimedAtText()));
        }
        if (mail.itemRewardItemId() != null && !mail.itemRewardItemId().isBlank()) {
            lines.add(Component.literal("itemReward: " + mail.itemRewardItemId()
                    + " x" + numberOrDash(mail.itemRewardQuantity())));
        }

        lines.add(Component.literal("message:"));
        lines.addAll(wrapText(font, safe(mail.message()), Math.max(60, contentWidth)));
        return lines;
    }

    private static List<Component> wrapText(Font font, String value, int maxWidth) {
        List<Component> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            lines.add(Component.literal("-"));
            return lines;
        }

        String remaining = value;
        while (!remaining.isEmpty()) {
            String part = font.plainSubstrByWidth(remaining, maxWidth);
            if (part.isEmpty()) {
                break;
            }
            lines.add(Component.literal(part));
            remaining = remaining.substring(part.length());
        }
        if (lines.isEmpty()) {
            lines.add(Component.literal(value));
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
}
