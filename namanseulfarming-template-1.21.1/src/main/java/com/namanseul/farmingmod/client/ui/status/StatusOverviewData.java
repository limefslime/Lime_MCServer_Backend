package com.namanseul.farmingmod.client.ui.status;

import java.util.List;

public record StatusOverviewData(
        FocusSnapshot focus,
        List<RegionSnapshot> regions,
        List<EventSnapshot> activeEvents,
        List<EffectSnapshot> projectEffects,
        List<CompletionSnapshot> completedProjects,
        boolean partial
) {
    public StatusOverviewData {
        focus = focus == null ? FocusSnapshot.empty() : focus;
        regions = regions == null ? List.of() : List.copyOf(regions);
        activeEvents = activeEvents == null ? List.of() : List.copyOf(activeEvents);
        projectEffects = projectEffects == null ? List.of() : List.copyOf(projectEffects);
        completedProjects = completedProjects == null ? List.of() : List.copyOf(completedProjects);
    }

    public static StatusOverviewData empty() {
        return new StatusOverviewData(
                FocusSnapshot.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false
        );
    }

    public boolean hasAnySnapshot() {
        return focus.available()
                || !regions.isEmpty()
                || !activeEvents.isEmpty()
                || !projectEffects.isEmpty()
                || !completedProjects.isEmpty();
    }

    public int regionCount() {
        return regions.size();
    }

    public int activeEventCount() {
        return activeEvents.size();
    }

    public int activeProjectEffectCount() {
        return projectEffects.size();
    }

    public int completedProjectCount() {
        return completedProjects.size();
    }

    public int rewardReadyMailCount() {
        int total = 0;
        for (CompletionSnapshot completion : completedProjects) {
            total += Math.max(0, completion.rewardMailCount());
        }
        return total;
    }

    public int rewardReadyAmount() {
        int total = 0;
        for (CompletionSnapshot completion : completedProjects) {
            total += Math.max(0, completion.rewardTotalAmount());
        }
        return total;
    }

    public record FocusSnapshot(
            String region,
            String status,
            String sourceCategory,
            boolean available
    ) {
        public FocusSnapshot {
            region = region == null || region.isBlank() ? "-" : region;
            status = status == null ? "" : status;
            sourceCategory = sourceCategory == null ? "" : sourceCategory;
        }

        public static FocusSnapshot empty() {
            return new FocusSnapshot("-", "", "", false);
        }
    }

    public record RegionSnapshot(
            String region,
            int level,
            int progressPercent,
            int currentSellTotal,
            String dominantCategory
    ) {
        public RegionSnapshot {
            region = region == null || region.isBlank() ? "-" : region;
            dominantCategory = dominantCategory == null ? "" : dominantCategory;
            progressPercent = Math.max(0, Math.min(100, progressPercent));
        }
    }

    public record EventSnapshot(
            String title,
            String region,
            String effectLabel,
            String state,
            boolean runtimeActive
    ) {
        public EventSnapshot {
            title = title == null || title.isBlank() ? "Event" : title;
            region = region == null || region.isBlank() ? "-" : region;
            effectLabel = effectLabel == null ? "" : effectLabel;
            state = state == null ? "" : state;
        }
    }

    public record EffectSnapshot(
            String projectId,
            String target,
            String effectType,
            double effectValue,
            boolean active
    ) {
        public EffectSnapshot {
            projectId = projectId == null || projectId.isBlank() ? "-" : projectId;
            target = target == null ? "" : target;
            effectType = effectType == null ? "" : effectType;
        }
    }

    public record CompletionSnapshot(
            String projectId,
            boolean completed,
            boolean effectActive,
            int rewardMailCount,
            int rewardTotalAmount
    ) {
        public CompletionSnapshot {
            projectId = projectId == null || projectId.isBlank() ? "-" : projectId;
        }
    }
}
