import { getActiveEvents, getEvents } from "../event/event.service.js";
import { getCurrentFocus } from "../focus/focus.service.js";
import { getMailStats } from "../mail/mail.service.js";
import { getActiveEffects } from "../project-completion/projectCompletion.service.js";
import { getAllRegions } from "../region/region.service.js";

function buildDefaultFocusSection() {
  return {
    currentFocusRegion: null,
    status: null,
    summary: null,
  };
}

function buildDefaultEventsSection() {
  return {
    activeCount: 0,
    activeItems: [],
    totalCount: null,
  };
}

function buildDefaultProjectEffectsSection() {
  return {
    activeCount: 0,
    activeItems: [],
  };
}

function buildDefaultRegionsSection() {
  return {
    items: [],
    dominantCategory: null,
    mappedLegacyRegionKey: null,
    summary: null,
  };
}

function buildDefaultInvestmentsSection() {
  return {
    recentlyCompletedCount: null,
    summary: null,
  };
}

function buildDefaultMailSection() {
  return {
    totalCount: 0,
    unclaimedRewardCount: 0,
    projectCompletionMailCount: 0,
    projectRewardMailCount: 0,
  };
}

function normalizeArray(value) {
  return Array.isArray(value) ? value : [];
}

function buildFocusSectionItem(focus) {
  if (!focus || typeof focus !== "object") {
    return buildDefaultFocusSection();
  }

  return {
    currentFocusRegion:
      typeof focus.focusRegion === "string" && focus.focusRegion.trim().length > 0
        ? focus.focusRegion
        : null,
    status: typeof focus.status === "string" ? focus.status : null,
    summary: {
      calculatedAt: focus.calculatedAt ?? null,
      sourceCategory: focus.sourceCategory ?? null,
      recentSellTotals: focus.recentSellTotals ?? null,
      resolvedFromCurrentCategories:
        typeof focus.resolvedFromCurrentCategories === "boolean"
          ? focus.resolvedFromCurrentCategories
          : null,
    },
  };
}

function buildEventsSectionItem(activeItems, totalCount) {
  const safeItems = normalizeArray(activeItems);
  const safeTotalCount = Number.isInteger(totalCount) ? totalCount : null;

  return {
    activeCount: safeItems.length,
    activeItems: safeItems,
    totalCount: safeTotalCount,
  };
}

function buildProjectEffectsSectionItem(activeItems) {
  const safeItems = normalizeArray(activeItems);

  return {
    activeCount: safeItems.length,
    activeItems: safeItems,
  };
}

function resolveRegionsSummary(items) {
  const safeItems = normalizeArray(items);
  if (safeItems.length === 0) {
    return {
      dominantCategory: null,
      mappedLegacyRegionKey: null,
    };
  }

  const operations = safeItems[0]?.operations;
  if (operations && typeof operations === "object") {
    return {
      dominantCategory:
        typeof operations.dominantCategory === "string" ? operations.dominantCategory : null,
      mappedLegacyRegionKey:
        typeof operations.mappedLegacyRegionKey === "string"
          ? operations.mappedLegacyRegionKey
          : null,
    };
  }

  return {
    dominantCategory: null,
    mappedLegacyRegionKey: null,
  };
}

function buildRegionsSectionItem(items) {
  const safeItems = normalizeArray(items);
  const summary = resolveRegionsSummary(safeItems);
  const operations = safeItems[0]?.operations;
  const summaryPayload =
    operations && typeof operations === "object"
      ? {
          operations,
          itemCount: safeItems.length,
        }
      : null;

  return {
    items: safeItems,
    dominantCategory: summary.dominantCategory,
    mappedLegacyRegionKey: summary.mappedLegacyRegionKey,
    summary: summaryPayload,
  };
}

function extractCompletedProjectIdsFromEffects(activeEffects) {
  const safeEffects = normalizeArray(activeEffects);
  const projectIdSet = new Set();

  for (const effect of safeEffects) {
    if (typeof effect?.project_id === "string" && effect.project_id.trim().length > 0) {
      projectIdSet.add(effect.project_id);
    }
  }

  return [...projectIdSet];
}

function buildInvestmentsSectionItem(activeEffects) {
  const completedProjectIds = extractCompletedProjectIdsFromEffects(activeEffects);
  const completionCount = completedProjectIds.length;

  return {
    recentlyCompletedCount: completionCount,
    summary: {
      completionSource: "active_project_effects",
      completedProjectCount: completionCount,
      activeEffectProjectCount: completionCount,
      completedProjectIds,
    },
  };
}

async function loadFocusSection() {
  try {
    const focus = await getCurrentFocus();
    return buildFocusSectionItem(focus);
  } catch (_error) {
    return buildDefaultFocusSection();
  }
}

async function loadEventsSection() {
  try {
    const [activeItems, allEvents] = await Promise.all([getActiveEvents(), getEvents()]);
    const totalCount = Array.isArray(allEvents) ? allEvents.length : null;
    return buildEventsSectionItem(activeItems, totalCount);
  } catch (_error) {
    return buildDefaultEventsSection();
  }
}

async function loadProjectEffectsSection() {
  try {
    const activeItems = await getActiveEffects();
    return buildProjectEffectsSectionItem(activeItems);
  } catch (_error) {
    return buildDefaultProjectEffectsSection();
  }
}

async function loadRegionsSection() {
  try {
    const items = await getAllRegions();
    return buildRegionsSectionItem(items);
  } catch (_error) {
    return buildDefaultRegionsSection();
  }
}

async function loadInvestmentsSection() {
  try {
    const activeEffects = await getActiveEffects();
    return buildInvestmentsSectionItem(activeEffects);
  } catch (_error) {
    return buildDefaultInvestmentsSection();
  }
}

async function loadMailSection() {
  try {
    const stats = await getMailStats();
    return {
      totalCount: Number(stats?.totalCount ?? 0),
      unclaimedRewardCount: Number(stats?.unclaimedRewardCount ?? 0),
      projectCompletionMailCount: Number(stats?.projectCompletionMailCount ?? 0),
      projectRewardMailCount: Number(stats?.projectRewardMailCount ?? 0),
    };
  } catch (_error) {
    return buildDefaultMailSection();
  }
}

export async function getOperationsSummary() {
  const [focus, events, projectEffects, regions, investments, mail] = await Promise.all([
    loadFocusSection(),
    loadEventsSection(),
    loadProjectEffectsSection(),
    loadRegionsSection(),
    loadInvestmentsSection(),
    loadMailSection(),
  ]);

  return {
    generatedAt: new Date().toISOString(),
    focus,
    events,
    projectEffects,
    regions,
    investments,
    mail,
  };
}
