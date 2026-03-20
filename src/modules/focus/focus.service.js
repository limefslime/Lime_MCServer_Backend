import {
  findCurrentFocusState,
  findRecentSellTotalsByCategory,
  upsertFocusState,
} from "./focus.repository.js";

const FOCUS_STATUS_ACTIVE = "active";
const FOCUS_CATEGORIES = ["mining", "farming", "fishing", "misc"];
const FOCUS_CATEGORY_SET = new Set(FOCUS_CATEGORIES);
const LEGACY_FOCUS_REGION_MAP = {
  agri: "farming",
  port: "fishing",
  industry: "mining",
};

function getDefaultFocusRegion() {
  return FOCUS_CATEGORIES[0];
}

function normalizeFocusRegion(focusRegion) {
  if (typeof focusRegion !== "string" || focusRegion.trim().length === 0) {
    throw new Error("invalid focus region");
  }

  const trimmedRegion = focusRegion.trim();
  const normalizedRegion = LEGACY_FOCUS_REGION_MAP[trimmedRegion] ?? trimmedRegion;
  return normalizedRegion;
}

function validateFocusId(focusId) {
  if (!Number.isInteger(focusId) || focusId <= 0) {
    throw new Error("invalid focus id");
  }
}

function validateFocusRegion(focusRegion) {
  const normalizedRegion = normalizeFocusRegion(focusRegion);
  if (!FOCUS_CATEGORY_SET.has(normalizedRegion)) {
    throw new Error("invalid focus region");
  }
}

function validateFocusStatus(status) {
  if (status !== FOCUS_STATUS_ACTIVE) {
    throw new Error("invalid focus status");
  }
}

function normalizeTotalPrice(totalPrice) {
  const numericValue = Number(totalPrice);
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return 0;
  }
  return numericValue;
}

function buildBaseTotals() {
  return Object.fromEntries(FOCUS_CATEGORIES.map((category) => [category, 0]));
}

function assertFocusRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && Number.isInteger(Number(row.id));
  const hasRegion = isObject && typeof row.focus_region === "string";
  const hasStatus = isObject && typeof row.status === "string";
  const hasCalculatedAt = isObject && Boolean(row.calculated_at);

  if (!isObject || !hasId || !hasRegion || !hasStatus || !hasCalculatedAt) {
    throw new Error("invalid focus row");
  }

  validateFocusId(Number(row.id));
  validateFocusRegion(row.focus_region);
  validateFocusStatus(row.status);
}

function normalizeFocusRow(row) {
  assertFocusRow(row);
  const normalizedRegion = normalizeFocusRegion(row.focus_region);
  validateFocusRegion(normalizedRegion);

  return {
    id: Number(row.id),
    focus_region: normalizedRegion,
    status: row.status,
    calculated_at: row.calculated_at,
  };
}

function assertSellTotalsRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasCategory = isObject && typeof row.category === "string";
  const hasTotalPrice = isObject && Number.isFinite(Number(row.total_price));

  if (!isObject || !hasCategory || !hasTotalPrice) {
    throw new Error("invalid sell totals row");
  }
}

function buildFocusResult(row) {
  const normalized = normalizeFocusRow(row);

  return {
    focusRegion: normalized.focus_region,
    status: normalized.status,
    calculatedAt: normalized.calculated_at,
    sourceCategory: normalized.focus_region,
    recentSellTotals: buildBaseTotals(),
    resolvedFromCurrentCategories: true,
  };
}

async function loadRecentSellTotals() {
  const totals = buildBaseTotals();
  const rows = await findRecentSellTotalsByCategory();

  for (const row of rows) {
    assertSellTotalsRow(row);
    if (!FOCUS_CATEGORY_SET.has(row.category)) {
      continue;
    }
    totals[row.category] = normalizeTotalPrice(row.total_price);
  }

  return totals;
}

function chooseFocusRegion(totals, currentFocusRegion) {
  const maxTotal = Math.max(...FOCUS_CATEGORIES.map((category) => totals[category] ?? 0));

  if (maxTotal <= 0) {
    return currentFocusRegion ?? getDefaultFocusRegion();
  }

  const winners = FOCUS_CATEGORIES.filter((category) => totals[category] === maxTotal);

  if (winners.length === 1) {
    return winners[0];
  }

  if (currentFocusRegion && winners.includes(currentFocusRegion)) {
    return currentFocusRegion;
  }

  return getDefaultFocusRegion();
}

async function getCurrentFocusStateOrCreateDefault() {
  let state = await findCurrentFocusState();

  if (!state) {
    state = await upsertFocusState(getDefaultFocusRegion());
  }

  return normalizeFocusRow(state);
}

export async function getCurrentFocus() {
  const state = await getCurrentFocusStateOrCreateDefault();
  const recentSellTotals = await loadRecentSellTotals();

  return {
    ...buildFocusResult(state),
    recentSellTotals,
  };
}

export async function recalculateFocus() {
  const currentState = await findCurrentFocusState();
  const normalizedCurrentState = currentState ? normalizeFocusRow(currentState) : null;

  const totals = await loadRecentSellTotals();

  const focusRegion = chooseFocusRegion(totals, normalizedCurrentState?.focus_region ?? null);
  validateFocusRegion(focusRegion);

  const updated = await upsertFocusState(focusRegion);
  const normalizedUpdated = normalizeFocusRow(updated);

  return {
    focusRegion: normalizedUpdated.focus_region,
    status: normalizedUpdated.status,
    sourceCategory: normalizedUpdated.focus_region,
    totals,
    recentSellTotals: totals,
    resolvedFromCurrentCategories: true,
    calculatedAt: normalizedUpdated.calculated_at,
  };
}
