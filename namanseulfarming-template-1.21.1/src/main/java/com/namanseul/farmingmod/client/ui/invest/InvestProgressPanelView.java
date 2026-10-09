package com.namanseul.farmingmod.client.ui.invest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class InvestProgressPanelView {
    private InvestProgressPanelView() {}

    public static void render(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            @Nullable InvestProjectViewData project,
            @Nullable InvestInvestmentResultViewData lastResult
    ) {
        List<Component> lines = buildLines(project, lastResult);
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
            @Nullable InvestProjectViewData project,
            @Nullable InvestInvestmentResultViewData lastResult
    ) {
        List<Component> lines = new ArrayList<>();
        if (project == null) {
            lines.add(Component.translatable("screen.namanseulfarming.invest.progress_waiting"));
            return lines;
        }

        JsonObject completion = project.completion();
        if (completion == null) {
            lines.add(Component.literal("완료 정보: 불러오지 못함"));
        } else {
            lines.add(Component.literal("목표 달성: " + readBoolean(completion, "reachedTarget",
                    flag(completion, "isCompleted"))));
            lines.add(Component.literal("효과 적용: " + readBoolean(completion, "activatedEffect",
                    flag(completion, "isEffectActive"))));
            lines.add(Component.literal("기존 완료 여부: " + readBoolean(completion, "wasAlreadyCompleted", false)));
            lines.add(Component.literal("효과 적용 구역: " + readString(completion, "effectTarget", "-")));
            lines.add(Component.literal("효과 종류: " + readString(completion, "effectType", "-")));
            lines.add(Component.literal("완료 우편 발송: " + readBoolean(completion, "createdCompletionMail",
                    flag(completion, "completionMailSent"))));
            lines.add(Component.literal("완료 처리: " + readBoolean(completion, "completionProcessed", false)));
            lines.add(Component.literal("보상 우편 수: " + readInt(completion, "rewardMailCount", 0)));
            lines.add(Component.literal("총 보상 금액: " + readInt(completion, "rewardTotalAmount", 0)));
        }

        if (lastResult != null) {
            lines.add(Component.literal("최근 기여: " + safeNumber(lastResult.investedAmount())
                    + " (전체 기여금 " + safeNumber(lastResult.projectTotal()) + ")"));
        }

        return lines;
    }

    private static boolean flag(JsonObject root,String key){try{return root.has(key)&&root.get(key).getAsBoolean();}catch(Exception ignored){return false;}}

    private static String readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback ? "예" : "아니오";
        }
        try {
            return element.getAsBoolean() ? "예" : "아니오";
        } catch (Exception ignored) {
            return fallback ? "예" : "아니오";
        }
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            try {
                return Math.round(element.getAsFloat());
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private static String readString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return com.namanseul.farmingmod.network.UiKoreanText.value(element.getAsString());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safeNumber(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }
}
