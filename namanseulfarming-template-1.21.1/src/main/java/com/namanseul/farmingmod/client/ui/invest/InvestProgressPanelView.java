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
            lines.add(Component.literal("completion: (not loaded)"));
        } else {
            lines.add(Component.literal("reachedTarget: " + readBoolean(completion, "reachedTarget",
                    readBoolean(completion, "isCompleted", false))));
            lines.add(Component.literal("activatedEffect: " + readBoolean(completion, "activatedEffect",
                    readBoolean(completion, "isEffectActive", false))));
            lines.add(Component.literal("wasAlreadyCompleted: " + readBoolean(completion, "wasAlreadyCompleted", false)));
            lines.add(Component.literal("effectTarget: " + readString(completion, "effectTarget", "-")));
            lines.add(Component.literal("effectType: " + readString(completion, "effectType", "-")));
            lines.add(Component.literal("createdCompletionMail: " + readBoolean(completion, "createdCompletionMail",
                    readBoolean(completion, "completionMailSent", false))));
            lines.add(Component.literal("completionProcessed: " + readBoolean(completion, "completionProcessed", false)));
            lines.add(Component.literal("rewardMailCount: " + readInt(completion, "rewardMailCount", 0)));
            lines.add(Component.literal("rewardTotalAmount: " + readInt(completion, "rewardTotalAmount", 0)));
        }

        if (lastResult != null) {
            lines.add(Component.literal("last invest: " + safeNumber(lastResult.investedAmount())
                    + " (projectTotal " + safeNumber(lastResult.projectTotal()) + ")"));
        }

        return lines;
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
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
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String safeNumber(@Nullable Integer value) {
        return value == null ? "-" : Integer.toString(value);
    }
}
