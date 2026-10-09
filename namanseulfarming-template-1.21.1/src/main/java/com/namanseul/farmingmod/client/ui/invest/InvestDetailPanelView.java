package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestDetailPanelView {
    private InvestDetailPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable InvestProjectViewData project
    ) {
        List<Component> lines = buildLines(project);
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

    private static List<Component> buildLines(@Nullable InvestProjectViewData project) {
        List<Component> lines = new ArrayList<>();
        if (project == null) {
            lines.add(Component.translatable("screen.namanseulfarming.invest.no_selection"));
            return lines;
        }

        lines.add(Component.literal("프로젝트 ID: " + safe(project.projectId())));
        lines.add(Component.literal("이름: " + safe(project.name())));
        lines.add(Component.literal("상태: " + com.namanseul.farmingmod.network.UiKoreanText.value(project.status())));
        lines.add(Component.literal("현재 기여금: " + safeNumber(project.currentAmount())));
        lines.add(Component.literal("목표 금액: " + safeNumber(project.targetAmount())));
        lines.add(Component.literal("남은 금액: " + safeNumber(project.remainingAmount())));
        lines.add(Component.literal("진행률: " + safePercent(project.progressPercent())));
        lines.add(Component.literal("내 기여금: " + safeDecimal(project.contributionAmount())));
        lines.add(Component.literal("참여자 수: " + safeNumber(project.contributors())));
        if (project.description() != null && !project.description().isBlank()) {
            lines.add(Component.literal("설명: " + project.description()));
        }
        return lines;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String safeNumber(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }

    private static String safeDecimal(@Nullable Double value) {
        if (value == null) {
            return "-";
        }
        return String.format("%.0f", value);
    }

    private static String safePercent(@Nullable Double value) {
        if (value == null) {
            return "-";
        }
        double ratio = value <= 1.0 ? value * 100.0 : value;
        return String.format("%.1f%%", ratio);
    }
}
