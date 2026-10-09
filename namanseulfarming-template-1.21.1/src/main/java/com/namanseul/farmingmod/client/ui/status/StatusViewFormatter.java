package com.namanseul.farmingmod.client.ui.status;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class StatusViewFormatter {
    public static final String TAB_FOCUS = "focus";
    public static final String TAB_REGION = "region";
    public static final String TAB_EVENT = "event";
    public static final String TAB_COMPLETION = "completion";

    private StatusViewFormatter() {}

    public static List<Component> buildListEntries(@Nullable StatusOverviewData data, String tabId) {
        if (data == null) {
            return List.of(Component.literal("월드 현황을 불러오는 중…"));
        }

        return switch (tabId) {
            case TAB_FOCUS -> buildFocusEntries(data);
            case TAB_REGION -> buildRegionEntries(data);
            case TAB_EVENT -> buildEventEntries(data);
            case TAB_COMPLETION -> buildCompletionEntries(data);
            default -> List.of();
        };
    }

    public static List<Component> buildDetailLines(@Nullable StatusOverviewData data, String tabId, int selectedIndex) {
        if (data == null) {
            return List.of(Component.literal("불러오기가 끝나면 상세 현황이 표시됩니다."));
        }

        return switch (tabId) {
            case TAB_FOCUS -> buildFocusDetail(data);
            case TAB_REGION -> buildRegionDetail(data, selectedIndex);
            case TAB_EVENT -> buildEventDetail(data, selectedIndex);
            case TAB_COMPLETION -> buildCompletionDetail(data, selectedIndex);
            default -> List.of();
        };
    }

    public static List<Component> buildSummaryLines(@Nullable StatusOverviewData data) {
        if (data == null) {
            return List.of(Component.literal("월드 현황을 불러오는 중…"));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("집중 구역: " + data.focus().region()));
        lines.add(Component.literal("진행 중인 이벤트: " + data.activeEventCount()));

        int rewardsReady = rewardReadyMailCount(data);
        if (rewardsReady > 0) {
            lines.add(Component.literal("수령 가능한 프로젝트 보상: " + rewardsReady + " mails"));
        } else {
            lines.add(Component.literal("완료된 프로젝트: " + data.completedProjectCount()));
        }

        if (data.partial()) {
            lines.add(Component.literal("일부 정보를 갱신하는 중입니다."));
        }
        return lines;
    }

    private static List<Component> buildFocusEntries(StatusOverviewData data) {
        if (!data.focus().available()) {
            return List.of(Component.literal("현재 집중 구역이 없습니다."));
        }

        List<Component> entries = new ArrayList<>();
        entries.add(Component.literal("현재 집중 구역: " + data.focus().region()));
        if (!data.focus().status().isBlank()) {
            entries.add(Component.literal("상태: " + data.focus().status()));
        }
        return entries;
    }

    private static List<Component> buildFocusDetail(StatusOverviewData data) {
        if (!data.focus().available()) {
            return List.of(Component.literal("집중 구역 정보가 없습니다."));
        }

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("주요 활동 구역: " + data.focus().region()));
        if (!data.focus().status().isBlank()) {
            lines.add(Component.literal("현재 상태: " + data.focus().status()));
        }
        return lines;
    }

    private static List<Component> buildRegionEntries(StatusOverviewData data) {
        if (data.regions().isEmpty()) {
            return List.of(Component.literal("구역 진행 정보가 없습니다."));
        }

        List<Component> entries = new ArrayList<>();
        for (StatusOverviewData.RegionSnapshot region : data.regions()) {
            entries.add(Component.literal(region.region() + " | " + region.progressPercent() + "%"));
        }
        return entries;
    }

    private static List<Component> buildRegionDetail(StatusOverviewData data, int selectedIndex) {
        if (data.regions().isEmpty()) {
            return List.of(Component.literal("구역 상세 정보가 없습니다."));
        }

        StatusOverviewData.RegionSnapshot selected = data.regions().get(clampIndex(selectedIndex, data.regions().size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("구역: " + selected.region()));
        lines.add(Component.literal("진행도: " + selected.progressPercent() + "%"));
        lines.add(Component.literal("레벨: " + selected.level()));
        if (!selected.dominantCategory().isBlank()) {
            lines.add(Component.literal("주요 분류: " + selected.dominantCategory()));
        }
        return lines;
    }

    private static List<Component> buildEventEntries(StatusOverviewData data) {
        if (data.activeEvents().isEmpty()) {
            return List.of(Component.literal("현재 진행 중인 이벤트가 없습니다."));
        }

        List<Component> entries = new ArrayList<>();
        for (StatusOverviewData.EventSnapshot event : data.activeEvents()) {
            entries.add(Component.literal(event.title() + " | " + eventState(event)));
        }
        return entries;
    }

    private static List<Component> buildEventDetail(StatusOverviewData data, int selectedIndex) {
        if (data.activeEvents().isEmpty()) {
            return List.of(Component.literal("이벤트 상세 정보가 없습니다."));
        }

        StatusOverviewData.EventSnapshot selected = data.activeEvents().get(clampIndex(selectedIndex, data.activeEvents().size()));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("이벤트: " + selected.title()));
        lines.add(Component.literal("상태: " + eventState(selected)));
        if (!selected.region().isBlank() && !"-".equals(selected.region())) {
            lines.add(Component.literal("구역: " + selected.region()));
        }
        if (!selected.effectLabel().isBlank()) {
            lines.add(Component.literal("효과: " + selected.effectLabel()));
        }
        return lines;
    }

    private static List<Component> buildCompletionEntries(StatusOverviewData data) {
        if (!data.completedProjects().isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (StatusOverviewData.CompletionSnapshot row : data.completedProjects()) {
                entries.add(Component.literal(row.projectId() + " | " + completionState(row)));
            }
            return entries;
        }

        if (!data.projectEffects().isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (StatusOverviewData.EffectSnapshot effect : data.projectEffects()) {
                entries.add(Component.literal(effect.projectId() + " | " + (effect.active() ? "효과 활성" : "효과 중지")));
            }
            return entries;
        }

        return List.of(Component.literal("프로젝트 완료 기록이 없습니다."));
    }

    private static List<Component> buildCompletionDetail(StatusOverviewData data, int selectedIndex) {
        if (!data.completedProjects().isEmpty()) {
            StatusOverviewData.CompletionSnapshot row = data.completedProjects().get(clampIndex(selectedIndex, data.completedProjects().size()));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("프로젝트: " + row.projectId()));
            lines.add(Component.literal("상태: " + completionState(row)));
            if (row.rewardMailCount() > 0) {
                lines.add(Component.literal("보상 우편 수: " + row.rewardMailCount()));
                lines.add(Component.literal("보상 금액: " + formatNumber(row.rewardTotalAmount())));
            }
            return lines;
        }

        if (!data.projectEffects().isEmpty()) {
            StatusOverviewData.EffectSnapshot effect = data.projectEffects().get(clampIndex(selectedIndex, data.projectEffects().size()));
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("프로젝트: " + effect.projectId()));
            lines.add(Component.literal("상태: " + (effect.active() ? "활성" : "중지")));
            lines.add(Component.literal("효과: " + buildEffectText(effect)));
            return lines;
        }

        return List.of(Component.literal("완료 상세 정보가 없습니다."));
    }

    private static String buildEffectText(StatusOverviewData.EffectSnapshot effect) {
        StringBuilder text = new StringBuilder();
        if (!effect.target().isBlank()) {
            text.append(effect.target()).append(" ");
        }
        if (!effect.effectType().isBlank()) {
            text.append(effect.effectType()).append(" ");
        }
        text.append(formatEffectValue(effect.effectValue()));
        return text.toString().trim();
    }

    private static String formatEffectValue(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format("%.2f", value);
    }

    private static String formatNumber(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static int rewardReadyMailCount(StatusOverviewData data) {
        int count = 0;
        for (StatusOverviewData.CompletionSnapshot row : data.completedProjects()) {
            count += Math.max(0, row.rewardMailCount());
        }
        return count;
    }

    private static String completionState(StatusOverviewData.CompletionSnapshot row) {
        if (row.completed() && row.rewardMailCount() > 0) {
            return "보상 수령 가능";
        }
        if (row.completed()) {
            return "완료";
        }
        return "진행 중";
    }

    private static String eventState(StatusOverviewData.EventSnapshot event) {
        if (event.runtimeActive()) {
            return "진행 중";
        }
        if (event.state() != null && !event.state().isBlank()) {
            return event.state();
        }
        return "중지";
    }

    private static int clampIndex(int selectedIndex, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(selectedIndex, size - 1));
    }
}
