import { withTransaction } from "../../db/pool.js";
import { getActiveEvents } from "../event/event.service.js";
import { getCurrentFocus } from "../focus/focus.service.js";
import { getActiveEffects } from "../project-completion/projectCompletion.service.js";
import {
  findAllRegionProgress,
  findRegionProgress,
  findSellTotalsByCategory,
  upsertRegionProgress,
} from "./region.repository.js";

class RegionServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "RegionServiceError";
    this.code = code;
  }
}

export const regionErrorCode = {
  INVALID_REGION: "INVALID_REGION",
  REGION_NOT_FOUND: "REGION_NOT_FOUND",
};

const REGIONS = ["agri", "port", "industry"];
const REGION_SET = new Set(REGIONS);

const CURRENT_SELL_CATEGORIES = ["farming", "fishing", "mining"];
const CURRENT_SELL_CATEGORY_SET = new Set(CURRENT_SELL_CATEGORIES);
const CURRENT_CATEGORY_TO_LEGACY_REGION = {
  farming: "agri",
  fishing: "port",
  mining: "industry",
};
const LEGACY_REGION_TO_CURRENT_CATEGORY = {
  agri: "farming",
  port: "fishing",
  industry: "mining",
};

export function isRegionServiceError(error) {
  return error instanceof RegionServiceError;
}

function validateRegion(region) {
  if (!REGION_SET.has(region)) {
    throw new RegionServiceError(regionErrorCode.INVALID_REGION, "region must be agri, port, or industry");
  }
}

function validateXp(xp) {
  if (!Number.isInteger(xp) || xp < 0) {
    throw new Error("invalid region xp");
  }
}

function validateLevel(level) {
  if (!Number.isInteger(level) || level < 1) {
    throw new Error("invalid region level");
  }
}

function normalizeNumeric(value) {
  return Number(value);
}

function normalizeSellTotal(value) {
  const numericValue = normalizeNumeric(value);
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return 0;
  }
  return numericValue;
}

function buildBaseTotals() {
  return Object.fromEntries(REGIONS.map((region) => [region, 0]));
}

function buildBaseSellTotals() {
  return Object.fromEntries(CURRENT_SELL_CATEGORIES.map((category) => [category, 0]));
}

function buildSellTotalsResult(sellTotals) {
  const source = sellTotals ?? {};
  return {
    farming: normalizeSellTotal(source.farming),
    fishing: normalizeSellTotal(source.fishing),
    mining: normalizeSellTotal(source.mining),
  };
}

function assertRegionRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasRegion = isObject && typeof row.region === "string";
  const hasXp = isObject && Number.isFinite(normalizeNumeric(row.xp));
  const hasLevel = isObject && Number.isFinite(normalizeNumeric(row.level));
  const hasUpdatedAt = isObject && Boolean(row.updated_at);

  if (!isObject || !hasRegion || !hasXp || !hasLevel || !hasUpdatedAt) {
    throw new Error("invalid region row");
  }

  validateRegion(row.region);
  validateXp(normalizeNumeric(row.xp));
  validateLevel(normalizeNumeric(row.level));
}

function assertSellTotalRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasCategory = isObject && typeof row.category === "string";
  const hasTotalPrice = isObject && Number.isFinite(normalizeNumeric(row.total_price));

  if (!isObject || !hasCategory || !hasTotalPrice) {
    throw new Error("invalid region sell totals row");
  }
}

function normalizeRegionSellTotals(rows) {
  const totals = buildBaseSellTotals();
  if (!Array.isArray(rows)) {
    return totals;
  }

  for (const row of rows) {
    assertSellTotalRow(row);
    if (!CURRENT_SELL_CATEGORY_SET.has(row.category)) {
      continue;
    }
    totals[row.category] += normalizeSellTotal(row.total_price);
  }

  return totals;
}

function resolveDominantCategory(totals) {
  let dominantCategory = null;
  let dominantTotal = 0;

  for (const category of CURRENT_SELL_CATEGORIES) {
    const total = normalizeSellTotal(totals?.[category]);
    if (total > dominantTotal) {
      dominantCategory = category;
      dominantTotal = total;
    }
  }

  return dominantTotal > 0 ? dominantCategory : null;
}

function mapCurrentCategoryToLegacyRegionKey(category) {
  if (typeof category !== "string") {
    return null;
  }
  return CURRENT_CATEGORY_TO_LEGACY_REGION[category] ?? null;
}

function mapLegacyRegionKeyToCurrentCategory(region) {
  if (typeof region !== "string") {
    return null;
  }
  return LEGACY_REGION_TO_CURRENT_CATEGORY[region] ?? null;
}

function calculateRegionProgress(rows) {
  const sellTotals = normalizeRegionSellTotals(rows);
  const legacyTotals = buildBaseTotals();

  for (const category of CURRENT_SELL_CATEGORIES) {
    const legacyRegion = mapCurrentCategoryToLegacyRegionKey(category);
    if (!legacyRegion) {
      continue;
    }
    legacyTotals[legacyRegion] += sellTotals[category];
  }

  const currentSellTotal = CURRENT_SELL_CATEGORIES.reduce(
    (sum, category) => sum + sellTotals[category],
    0
  );
  const dominantCategory = resolveDominantCategory(sellTotals);
  const mappedLegacyRegionKey = mapCurrentCategoryToLegacyRegionKey(dominantCategory);

  return {
    sellTotals,
    legacyTotals,
    currentSellTotal,
    dominantCategory,
    mappedLegacyRegionKey,
  };
}

function buildRegionScopedMetrics(region, progress) {
  const category = mapLegacyRegionKeyToCurrentCategory(region);
  const currentSellTotal = category
    ? normalizeSellTotal(progress?.sellTotals?.[category])
    : 0;

  return {
    currentSellTotal,
    dominantCategory: currentSellTotal > 0 ? category : null,
    mappedLegacyRegionKey: currentSellTotal > 0 ? region : null,
  };
}

function getRequiredXpForLevel(level) {
  return 50 * (level - 1) * level;
}

function calculateProgressPercent(xp, level) {
  const safeLevel = Math.max(1, Number(level) || 1);
  const safeXp = Math.max(0, Number(xp) || 0);
  const currentLevelRequired = getRequiredXpForLevel(safeLevel);
  const nextLevelRequired = getRequiredXpForLevel(safeLevel + 1);
  const levelRange = Math.max(1, nextLevelRequired - currentLevelRequired);
  const progress = (safeXp - currentLevelRequired) / levelRange;

  return Math.max(0, Math.min(1, progress));
}

function buildRegionOperationsSummary({
  dominantCategory = null,
  mappedLegacyRegionKey = null,
  currentFocusRegion = null,
  activeEventCount = 0,
  activeProjectEffectCount = 0,
}) {
  return {
    dominantCategory,
    mappedLegacyRegionKey,
    currentFocusRegion,
    activeEventCount: Number.isFinite(Number(activeEventCount)) ? Number(activeEventCount) : 0,
    activeProjectEffectCount: Number.isFinite(Number(activeProjectEffectCount))
      ? Number(activeProjectEffectCount)
      : 0,
  };
}

async function buildOperationsSummaryForRegion(progress) {
  try {
    const [focusState, activeEvents, activeProjectEffects] = await Promise.all([
      getCurrentFocus(),
      getActiveEvents(),
      getActiveEffects(),
    ]);

    return buildRegionOperationsSummary({
      dominantCategory: progress.dominantCategory,
      mappedLegacyRegionKey: progress.mappedLegacyRegionKey,
      currentFocusRegion: focusState?.focusRegion ?? null,
      activeEventCount: Array.isArray(activeEvents) ? activeEvents.length : 0,
      activeProjectEffectCount: Array.isArray(activeProjectEffects) ? activeProjectEffects.length : 0,
    });
  } catch (_error) {
    return buildRegionOperationsSummary({
      dominantCategory: progress.dominantCategory,
      mappedLegacyRegionKey: progress.mappedLegacyRegionKey,
      currentFocusRegion: null,
      activeEventCount: 0,
      activeProjectEffectCount: 0,
    });
  }
}

function buildRegionResult(row, metrics = {}) {
  assertRegionRow(row);

  const xp = normalizeNumeric(row.xp);
  const level = normalizeNumeric(row.level);
  const sellTotals = buildSellTotalsResult(metrics.sellTotals);
  const globalDominantCategory = resolveDominantCategory(sellTotals);
  const globalMappedLegacyRegionKey = mapCurrentCategoryToLegacyRegionKey(globalDominantCategory);
  const scopedMetrics = buildRegionScopedMetrics(row.region, {
    sellTotals,
  });
  const operations =
    metrics.operations ??
    buildRegionOperationsSummary({
      dominantCategory: globalDominantCategory,
      mappedLegacyRegionKey: globalMappedLegacyRegionKey,
      currentFocusRegion: null,
      activeEventCount: 0,
      activeProjectEffectCount: 0,
    });

  return {
    region: row.region,
    xp,
    level,
    updatedAt: row.updated_at,
    currentSellTotal: scopedMetrics.currentSellTotal,
    sellTotals,
    dominantCategory: scopedMetrics.dominantCategory,
    mappedLegacyRegionKey: scopedMetrics.mappedLegacyRegionKey,
    progressPercent: metrics.progressPercent ?? calculateProgressPercent(xp, level),
    operations,
  };
}

function buildRegionListItem(row, metrics) {
  return buildRegionResult(row, metrics);
}

function buildRegionProgressResult(row, metrics = {}) {
  assertRegionRow(row);

  const xp = normalizeNumeric(row.xp);
  const level = normalizeNumeric(row.level);
  const sellTotals = buildSellTotalsResult(metrics.sellTotals);
  const globalDominantCategory = resolveDominantCategory(sellTotals);
  const globalMappedLegacyRegionKey = mapCurrentCategoryToLegacyRegionKey(globalDominantCategory);
  const scopedMetrics = buildRegionScopedMetrics(row.region, {
    sellTotals,
  });
  const operations =
    metrics.operations ??
    buildRegionOperationsSummary({
      dominantCategory: globalDominantCategory,
      mappedLegacyRegionKey: globalMappedLegacyRegionKey,
      currentFocusRegion: null,
      activeEventCount: 0,
      activeProjectEffectCount: 0,
    });

  return {
    region: row.region,
    xp,
    level,
    currentSellTotal: scopedMetrics.currentSellTotal,
    sellTotals,
    dominantCategory: scopedMetrics.dominantCategory,
    mappedLegacyRegionKey: scopedMetrics.mappedLegacyRegionKey,
    progressPercent: metrics.progressPercent ?? calculateProgressPercent(xp, level),
    operations,
  };
}

async function getRegionOrThrow(region, executor) {
  const row = await findRegionProgress(region, executor);
  if (!row) {
    throw new RegionServiceError(regionErrorCode.REGION_NOT_FOUND, "region not found");
  }
  assertRegionRow(row);
  return row;
}

/**
 * XP conversion:
 * - xp = floor(totalPrice / 10)
 * - if totalPrice > 0 and floor result is 0, minimum 1 XP
 */
export function calculateXpFromTotalPrice(totalPrice) {
  const safeTotal = Math.max(0, normalizeNumeric(totalPrice) || 0);
  const baseXp = Math.floor(safeTotal / 10);

  if (safeTotal > 0 && baseXp === 0) {
    return 1;
  }

  return baseXp;
}

/**
 * Cumulative XP-based level formula:
 * required XP for level n = 50 * (n - 1) * n
 * => 1->0, 2->100, 3->300, 4->600 ...
 */
export function calculateLevelFromXp(xp) {
  const safeXp = Math.max(0, normalizeNumeric(xp) || 0);
  let level = 1;

  while (safeXp >= 50 * level * (level + 1)) {
    level += 1;
  }

  return Math.max(1, level);
}

export async function getAllRegions() {
  const [rows, totalRows] = await Promise.all([findAllRegionProgress(), findSellTotalsByCategory()]);
  const progress = calculateRegionProgress(totalRows);
  const operations = await buildOperationsSummaryForRegion(progress);

  return rows.map((row) =>
    buildRegionListItem(row, {
      sellTotals: progress.sellTotals,
      operations,
    })
  );
}

export async function getRegion(region) {
  validateRegion(region);

  const [row, totalRows] = await Promise.all([getRegionOrThrow(region), findSellTotalsByCategory()]);
  const progress = calculateRegionProgress(totalRows);
  const operations = await buildOperationsSummaryForRegion(progress);

  return buildRegionResult(row, {
    sellTotals: progress.sellTotals,
    operations,
  });
}

export async function recalculateRegions() {
  const recalculated = await withTransaction(async (client) => {
    const totalRows = await findSellTotalsByCategory(client);
    const progress = calculateRegionProgress(totalRows);
    const recalculatedRows = [];

    for (const region of REGIONS) {
      const xp = calculateXpFromTotalPrice(progress.legacyTotals[region]);
      const level = calculateLevelFromXp(xp);
      const row = await upsertRegionProgress(region, xp, level, client);
      assertRegionRow(row);
      recalculatedRows.push(
        buildRegionProgressResult(row, {
          sellTotals: progress.sellTotals,
          progressPercent: calculateProgressPercent(xp, level),
        })
      );
    }

    return {
      regions: recalculatedRows,
      recalculatedAt: new Date().toISOString(),
      progress,
    };
  });

  const operations = await buildOperationsSummaryForRegion(recalculated.progress);
  const regions = recalculated.regions.map((region) => ({
    ...region,
    operations,
  }));

  return {
    regions,
    recalculatedAt: recalculated.recalculatedAt,
    operations,
  };
}
