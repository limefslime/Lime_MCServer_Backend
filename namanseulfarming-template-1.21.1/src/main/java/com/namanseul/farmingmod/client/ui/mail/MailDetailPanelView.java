package com.namanseul.farmingmod.client.ui.mail;

import com.namanseul.farmingmod.client.ui.widget.UiTextRender;
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
        List<Component> lines = buildLines(mail);
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

    private static List<Component> buildLines(@Nullable MailViewData mail) {
        List<Component> lines = new ArrayList<>();
        if (mail == null) {
            lines.add(Component.translatable("screen.namanseulfarming.mail.no_selection"));
            return lines;
        }

        lines.add(Component.literal("제목: " + com.namanseul.farmingmod.network.UiKoreanText.value(mail.title())));
        lines.add(Component.literal("우편 ID: " + safe(mail.id())));
        lines.add(Component.literal("우편 종류: " + com.namanseul.farmingmod.network.UiKoreanText.value(mail.mailType())));
        lines.add(Component.literal("수령 완료: " + (mail.claimed()?"예":"아니오")));
        lines.add(Component.literal("보상 포함: " + (mail.hasReward()?"예":"아니오")));
        lines.add(Component.literal("지급 금액: " + numberOrDash(mail.rewardAmount())));
        lines.add(Component.literal("보상 종류: " + com.namanseul.farmingmod.network.UiKoreanText.value(mail.rewardType())));
        lines.add(Component.literal("발송 시각: " + safe(mail.createdAtText())));
        if (mail.claimedAtText() != null && !mail.claimedAtText().isBlank()) {
            lines.add(Component.literal("수령 시각: " + mail.claimedAtText()));
        }
        if (mail.itemRewardItemId() != null && !mail.itemRewardItemId().isBlank()) {
            lines.add(Component.literal("아이템 보상: " + mail.itemRewardItemId()
                    + " x" + numberOrDash(mail.itemRewardQuantity())));
        }

        lines.add(Component.literal("내용: " + safe(mail.message())));
        return lines;
    }

    private static void drawStructuredLine(GuiGraphics graphics, Font font, String line, int x, int y, int width) {
        int colon = line.indexOf(':');
        if (colon > 0 && colon < line.length() - 1) {
            String label = line.substring(0, colon + 1).trim();
            String value = line.substring(colon + 1).trim();
            if (!value.isBlank() && label.length() <= 20) {
                int labelWidth = Math.max(52, Math.min(116, width / 2));
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
}
