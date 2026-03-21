import { withTransaction } from "../../db/pool.js";
import { getActiveEvents as getActiveEventRows } from "../event/event.repository.js";
import { findCurrentFocusState } from "../focus/focus.repository.js";
import { getActiveProjectEffects } from "../project-completion/projectCompletion.repository.js";
import { getVillageShopEffect } from "../village/village.service.js";
import {
  addBalance,
  ensurePlayerExists,
  ensureWalletExists,
  findWalletByPlayerId,
  insertLedgerEntry,
  subtractBalanceIfEnough,
} from "../wallet/wallet.repository.js";
import {
  decreaseShopItemStockIfEnough,
  findAllShopItems,
  findLatestShopTransactionByPlayerAndItem,
  findShopItemByItemId,
  increaseShopItemStock,
  insertShopTransaction,
  tryReplenishShopItemStock,
} from "./shop.repository.js";
import { calculateShopModifiers } from "./shopEffect.service.js";

class ShopServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "ShopServiceError";
    this.code = code;
  }
}

export const shopErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  ITEM_NOT_FOUND: "ITEM_NOT_FOUND",
  ITEM_INACTIVE: "ITEM_INACTIVE",
  INSUFFICIENT_BALANCE: "INSUFFICIENT_BALANCE",
  INSUFFICIENT_STOCK: "INSUFFICIENT_STOCK",
  ITEM_PRICE_NOT_TRADABLE: "ITEM_PRICE_NOT_TRADABLE",
  TRADE_COOLDOWN_ACTIVE: "TRADE_COOLDOWN_ACTIVE",
  SELL_QUANTITY_TOO_LARGE: "SELL_QUANTITY_TOO_LARGE",
};

const MAX_INT = 2147483647;
const MAX_SELL_LIMIT = 1000;
const RAPID_FLIP_COOLDOWN_MS = 10000;
const DEFAULT_BUY_FEE_RATE = 0.02;
const DEFAULT_SELL_FEE_RATE = 0.03;
const DEFAULT_MIN_FEE = 0;
const PROJECT_EFFECTS_CACHE_TTL_MS = 20000;
const ALLOWED_ITEM_CATEGORIES = new Set(["agri", "port", "industry", "misc"]);

const projectEffectsCache = {
  rows: null,
  expiresAt: 0,
  inFlight: null,
};

export function isShopServiceError(error) {
  return error instanceof ShopServiceError;
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, `${fieldName} must be a valid uuid`);
  }
}

function validateItemId(itemId) {
  if (typeof itemId !== "string" || itemId.trim().length === 0) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "itemId is required");
  }
}

function validateQuantity(quantity) {
  if (!Number.isInteger(quantity) || quantity <= 0) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "quantity must be a positive integer");
  }
}

function validateSellQuantityLimit(quantity) {
  if (quantity > MAX_SELL_LIMIT) {
    throw new ShopServiceError(
      shopErrorCode.SELL_QUANTITY_TOO_LARGE,
      `sell quantity must be ${MAX_SELL_LIMIT} or less`
    );
  }
}

function validateItemCategory(category) {
  if (!ALLOWED_ITEM_CATEGORIES.has(category)) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid item category");
  }
}

function validateShopItemRow(item) {
  const isObject = typeof item === "object" && item !== null;
  const hasItemId = isObject && typeof item.item_id === "string" && item.item_id.trim().length > 0;
  const hasCategory =
    isObject && typeof item.category === "string" && item.category.trim().length > 0;
  const hasValidBuyPrice = isObject && Number.isFinite(Number(item.buy_price));
  const hasValidSellPrice = isObject && Number.isFinite(Number(item.sell_price));
  const hasValidStockQuantity =
    isObject &&
    Number.isInteger(Number(item.stock_quantity)) &&
    Number(item.stock_quantity) >= 0;
  const hasValidReplenishAmount =
    isObject &&
    Number.isInteger(Number(item.replenish_amount)) &&
    Number(item.replenish_amount) >= 0;
  const hasValidReplenishInterval =
    isObject &&
    Number.isInteger(Number(item.replenish_interval_seconds)) &&
    Number(item.replenish_interval_seconds) >= 0;

  if (
    !isObject ||
    !hasItemId ||
    !hasCategory ||
    !hasValidBuyPrice ||
    !hasValidSellPrice ||
    !hasValidStockQuantity ||
    !hasValidReplenishAmount ||
    !hasValidReplenishInterval
  ) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid shop item data");
  }

  validateItemCategory(item.category);
}

function assertTradableItem(item) {
  if (!item.is_active) {
    throw new ShopServiceError(shopErrorCode.ITEM_INACTIVE, "item is inactive");
  }
}

function assertValidPricingContext(pricingContext) {
  if (typeof pricingContext !== "object" || pricingContext === null) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid pricing context");
  }
}

function assertValidTradeItem(item) {
  const isObject = typeof item === "object" && item !== null;
  const hasItemId = isObject && typeof item.item_id === "string" && item.item_id.trim().length > 0;
  if (!isObject || !hasItemId) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid trade item");
  }
}

function normalizeMultiplier(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return 1;
  }
  return numericValue;
}

function parseFeeRate(rawValue, fallback) {
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  if (parsed < 0) {
    return 0;
  }
  if (parsed >= 1) {
    return 0.99;
  }
  return parsed;
}

function parseNonNegativeInt(rawValue, fallback) {
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  const intValue = Math.floor(parsed);
  if (intValue < 0) {
    return 0;
  }
  return intValue;
}

function getShopFeeConfig() {
  return {
    buyFeeRate: parseFeeRate(process.env.SHOP_BUY_FEE_RATE, DEFAULT_BUY_FEE_RATE),
    sellFeeRate: parseFeeRate(process.env.SHOP_SELL_FEE_RATE, DEFAULT_SELL_FEE_RATE),
    minFee: parseNonNegativeInt(process.env.SHOP_MIN_FEE, DEFAULT_MIN_FEE),
  };
}

function calculateTradeFeeAmount(grossTotalPrice, feeRate, minFee) {
  if (grossTotalPrice <= 0 || feeRate <= 0) {
    return 0;
  }
  const computedFee = Math.ceil(grossTotalPrice * feeRate);
  return Math.min(MAX_INT, Math.max(minFee, computedFee));
}

function applyTradeFeeModel(transactionType, grossTotalPrice) {
  const feeConfig = getShopFeeConfig();
  const feeRate =
    transactionType === "sell" ? feeConfig.sellFeeRate : feeConfig.buyFeeRate;
  const feeAmount = calculateTradeFeeAmount(grossTotalPrice, feeRate, feeConfig.minFee);
  const settlementTotalPrice =
    transactionType === "sell"
      ? grossTotalPrice - feeAmount
      : grossTotalPrice + feeAmount;

  return {
    feeRate,
    feeAmount,
    grossTotalPrice,
    settlementTotalPrice,
  };
}

function assertSafeRoundedUnitPrice(unitPrice) {
  const numericUnitPrice = Number(unitPrice);
  if (!Number.isSafeInteger(numericUnitPrice)) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "unit price must be a safe integer");
  }
  return numericUnitPrice;
}

function validateUnitPriceAndTotalPrice(unitPrice, totalPrice, priceType) {
  if (unitPrice <= 0 || totalPrice <= 0) {
    throw new ShopServiceError(
      shopErrorCode.ITEM_PRICE_NOT_TRADABLE,
      `${priceType} price must be greater than zero`
    );
  }
  if (totalPrice > MAX_INT) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, `total ${priceType} price is too large`);
  }
}

function validateSettlementTotalPrice(totalPrice, priceType) {
  if (totalPrice <= 0) {
    throw new ShopServiceError(
      shopErrorCode.ITEM_PRICE_NOT_TRADABLE,
      `${priceType} price is too low after fee`
    );
  }
  if (totalPrice > MAX_INT) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, `total ${priceType} price is too large`);
  }
}

function normalizePricingContext(pricingContext) {
  return {
    currentFocusRegion: pricingContext?.currentFocusRegion ?? null,
    activeProjectEffects: pricingContext?.activeProjectEffects ?? [],
    activeEvents: pricingContext?.activeEvents ?? [],
    villageEffect: pricingContext?.villageEffect ?? null,
  };
}

function buildPricingContextWithModifiers(pricingContext) {
  return {
    currentFocusRegion: pricingContext?.currentFocusRegion ?? null,
    activeProjectEffects: pricingContext?.activeProjectEffects ?? [],
    activeEvents: pricingContext?.activeEvents ?? [],
    villageEffect: pricingContext?.villageEffect ?? null,
    modifiers: pricingContext?.modifiers ?? null,
  };
}

function applyShopModifiers(unitPrice, transactionType, modifiers) {
  const safeUnitPrice = Number(unitPrice);
  const buyMultiplier = normalizeMultiplier(modifiers?.buyPriceMultiplier);
  const sellMultiplier = normalizeMultiplier(modifiers?.sellPriceMultiplier);

  const multiplier = transactionType === "sell" ? sellMultiplier : buyMultiplier;
  const roundedUnitPrice = Math.round(safeUnitPrice * multiplier);
  return assertSafeRoundedUnitPrice(roundedUnitPrice);
}

function buildDefaultModifierBreakdown() {
  return {
    focus: {
      applied: false,
      target: null,
      multiplier: 1,
    },
    projectEffects: {
      applied: false,
      count: 0,
      multiplier: 1,
      targets: [],
    },
    events: {
      applied: false,
      count: 0,
      multiplier: 1,
      regions: [],
    },
    village: {
      applied: false,
      level: 1,
      discountRate: 0,
      buyMultiplier: 1,
    },
    totalBuyMultiplier: 1,
    totalSellMultiplier: 1,
    totalMultiplier: 1,
  };
}

function extractAppliedModifierSummary(modifiers) {
  const fallback = buildDefaultModifierBreakdown();
  const breakdown = modifiers?.breakdown;
  if (!breakdown || typeof breakdown !== "object") {
    return fallback;
  }

  return {
    focus: {
      applied: Boolean(breakdown?.focus?.applied),
      target: typeof breakdown?.focus?.target === "string" ? breakdown.focus.target : null,
      multiplier: normalizeMultiplier(breakdown?.focus?.multiplier),
    },
    projectEffects: {
      applied: Boolean(breakdown?.projectEffects?.applied),
      count: Number.isInteger(breakdown?.projectEffects?.count) ? breakdown.projectEffects.count : 0,
      multiplier: normalizeMultiplier(breakdown?.projectEffects?.multiplier),
      targets: Array.isArray(breakdown?.projectEffects?.targets)
        ? breakdown.projectEffects.targets
        : [],
    },
    events: {
      applied: Boolean(breakdown?.events?.applied),
      count: Number.isInteger(breakdown?.events?.count) ? breakdown.events.count : 0,
      multiplier: normalizeMultiplier(breakdown?.events?.multiplier),
      regions: Array.isArray(breakdown?.events?.regions) ? breakdown.events.regions : [],
    },
    village: {
      applied: Boolean(breakdown?.village?.applied),
      level: Number.isInteger(breakdown?.village?.level) ? breakdown.village.level : 1,
      discountRate: Number.isFinite(Number(breakdown?.village?.discountRate))
        ? Number(breakdown.village.discountRate)
        : 0,
      buyMultiplier: normalizeMultiplier(breakdown?.village?.buyMultiplier),
    },
    totalBuyMultiplier: normalizeMultiplier(
      breakdown?.totalBuyMultiplier ?? breakdown?.totalMultiplier
    ),
    totalSellMultiplier: normalizeMultiplier(
      breakdown?.totalSellMultiplier ?? breakdown?.totalMultiplier
    ),
    totalMultiplier: normalizeMultiplier(breakdown?.totalMultiplier),
  };
}

function buildShopPriceBreakdown({ transactionType, modifiers }) {
  const summary = extractAppliedModifierSummary(modifiers);
  const hasBreakdown = Boolean(modifiers && typeof modifiers === "object" && modifiers.breakdown);
  const fallbackMultiplier =
    transactionType === "sell"
      ? normalizeMultiplier(modifiers?.sellPriceMultiplier)
      : normalizeMultiplier(modifiers?.buyPriceMultiplier);
  const totalMultiplier = hasBreakdown
    ? transactionType === "sell"
      ? summary.totalSellMultiplier
      : summary.totalBuyMultiplier
    : fallbackMultiplier;

  return {
    totalMultiplier,
    modifiers: {
      focus: {
        applied: summary.focus.applied,
        multiplier: summary.focus.multiplier,
        target: summary.focus.target,
      },
      projectEffects: {
        applied: summary.projectEffects.applied,
        count: summary.projectEffects.count,
        multiplier: summary.projectEffects.multiplier,
      },
      events: {
        applied: summary.events.applied,
        count: summary.events.count,
        multiplier: summary.events.multiplier,
      },
      village: {
        applied: summary.village.applied,
        level: summary.village.level,
        discountRate: summary.village.discountRate,
        buyMultiplier: summary.village.buyMultiplier,
      },
    },
  };
}

function buildPricingReasonTags(modifierSummary, hasFee) {
  const tags = [];

  if (modifierSummary?.focus?.applied) {
    tags.push("focus");
  }
  if (modifierSummary?.projectEffects?.applied) {
    tags.push("project_effect");
  }
  if (modifierSummary?.events?.applied) {
    tags.push("event");
  }
  if (modifierSummary?.village?.applied) {
    tags.push("village");
  }
  if (hasFee) {
    tags.push("fee");
  }

  return tags;
}

function buildPricingSummaryText(modifierSummary, hasFee) {
  const reasonTags = buildPricingReasonTags(modifierSummary, hasFee);
  if (reasonTags.length === 0 && !hasFee) {
    return "현재 추가 보정이 없는 기본 가격입니다.";
  }

  const parts = [];

  if (modifierSummary?.focus?.applied) {
    parts.push("포커스 보정");
  }
  if (modifierSummary?.projectEffects?.applied) {
    const count = Number(modifierSummary?.projectEffects?.count) || 0;
    parts.push(`프로젝트 효과 ${count}개`);
  }
  if (modifierSummary?.events?.applied) {
    const count = Number(modifierSummary?.events?.count) || 0;
    parts.push(`이벤트 보정 ${count}개`);
  }
  if (modifierSummary?.village?.applied) {
    const level = Number(modifierSummary?.village?.level) || 1;
    parts.push(`마을 기금 할인(Lv.${level})`);
  }
  if (hasFee) {
    parts.push("거래 수수료");
  }

  if (parts.length === 1) {
    return `현재 ${parts[0]}이 적용된 가격입니다.`;
  }

  return `현재 ${parts.join(" 및 ")}가 적용된 가격입니다.`;
}

function buildShopPricingExplanation({
  item,
  pricingContext,
  unitPrice,
  quantity,
  grossTotalPrice,
  feeRate,
  feeAmount,
  totalPrice,
  transactionType,
}) {
  const safeTransactionType = transactionType === "sell" ? "sell" : "buy";
  const baseUnitPrice =
    safeTransactionType === "sell" ? Number(item.sell_price) : Number(item.buy_price);
  const priceBreakdown = buildShopPriceBreakdown({
    transactionType: safeTransactionType,
    modifiers: pricingContext?.modifiers,
  });
  const totalMultiplier = baseUnitPrice > 0 ? priceBreakdown.totalMultiplier : 1;
  const hasFee = Number(feeAmount) > 0;
  const pricingReasonTags = buildPricingReasonTags(priceBreakdown.modifiers, hasFee);
  const pricingSummary = buildPricingSummaryText(priceBreakdown.modifiers, hasFee);

  return {
    transactionType: safeTransactionType,
    category: item.category,
    baseUnitPrice,
    finalUnitPrice: Number(unitPrice),
    quantity: Number(quantity),
    grossTotalPrice: Number(grossTotalPrice),
    feeRate: Number(feeRate),
    feeAmount: Number(feeAmount),
    settlementTotalPrice: Number(totalPrice),
    totalPrice: Number(totalPrice),
    totalMultiplier,
    modifiers: priceBreakdown.modifiers,
    fee: {
      applied: hasFee,
      rate: Number(feeRate),
      amount: Number(feeAmount),
      mode: safeTransactionType === "sell" ? "deduction" : "surcharge",
    },
    pricingSummary,
    pricingReasonTags,
  };
}

function buildShopPreviewPricing({
  item,
  pricingContext,
  unitPrice,
  quantity,
  grossTotalPrice,
  feeRate,
  feeAmount,
  totalPrice,
  transactionType,
}) {
  return buildShopPricingExplanation({
    item,
    pricingContext,
    unitPrice,
    quantity,
    grossTotalPrice,
    feeRate,
    feeAmount,
    totalPrice,
    transactionType,
  });
}

function normalizeWalletBalance(balanceValue) {
  const numericBalance = Number(balanceValue);
  if (!Number.isFinite(numericBalance)) {
    return null;
  }
  return numericBalance;
}

function normalizeStockQuantity(stockValue) {
  const numericStock = Number(stockValue);
  if (!Number.isFinite(numericStock)) {
    return 0;
  }
  const floored = Math.floor(numericStock);
  if (floored <= 0) {
    return 0;
  }
  return Math.min(MAX_INT, floored);
}

async function refreshShopItemStockIfNeeded(item, executor) {
  const currentStock = normalizeStockQuantity(item?.stock_quantity);
  if (currentStock > 0) {
    return item;
  }

  const replenished = await tryReplenishShopItemStock(item.item_id, executor);
  return replenished ?? item;
}

function assertBuyStockAvailable(item, quantity) {
  const stockQuantity = normalizeStockQuantity(item?.stock_quantity);
  if (stockQuantity < quantity) {
    throw new ShopServiceError(
      shopErrorCode.INSUFFICIENT_STOCK,
      `insufficient stock: available=${stockQuantity}, required=${quantity}`
    );
  }
}

async function assertNoRapidFlipTrade(playerId, itemId, transactionType, executor) {
  const latest = await findLatestShopTransactionByPlayerAndItem(playerId, itemId, executor);
  if (!latest) {
    return;
  }

  if (latest.transaction_type === transactionType) {
    return;
  }

  const latestTradeAt = new Date(latest.created_at).getTime();
  if (!Number.isFinite(latestTradeAt)) {
    return;
  }

  const elapsedMs = Date.now() - latestTradeAt;
  if (elapsedMs >= RAPID_FLIP_COOLDOWN_MS) {
    return;
  }

  const remainingSec = Math.max(1, Math.ceil((RAPID_FLIP_COOLDOWN_MS - elapsedMs) / 1000));
  throw new ShopServiceError(
    shopErrorCode.TRADE_COOLDOWN_ACTIVE,
    `opposite trade cooldown active: wait ${remainingSec}s`
  );
}

function buildShopPreviewResult({
  transactionType,
  playerId,
  item,
  quantity,
  unitPrice,
  grossTotalPrice,
  feeRate,
  feeAmount,
  totalPrice,
  pricing,
  balanceBefore,
}) {
  const safeTransactionType = transactionType === "sell" ? "sell" : "buy";
  const normalizedBalanceBefore = normalizeWalletBalance(balanceBefore);
  const stockBefore = normalizeStockQuantity(item?.stock_quantity);
  const canUseBalance = normalizedBalanceBefore !== null;
  const balanceAfterPreview =
    canUseBalance && safeTransactionType === "buy"
      ? normalizedBalanceBefore - totalPrice
      : canUseBalance
        ? normalizedBalanceBefore + totalPrice
        : null;
  const stockAfterPreview =
    safeTransactionType === "buy"
      ? Math.max(0, stockBefore - quantity)
      : Math.min(MAX_INT, stockBefore + quantity);
  const canAfford =
    safeTransactionType === "sell"
      ? true
      : canUseBalance
        ? normalizedBalanceBefore >= totalPrice
        : null;

  return {
    preview: true,
    transactionType: safeTransactionType,
    playerId,
    itemId: item.item_id,
    quantity,
    unitPrice,
    grossTotalPrice,
    feeRate,
    feeAmount,
    netTotalPrice: totalPrice,
    totalPrice,
    balanceBefore: normalizedBalanceBefore,
    balanceAfterPreview,
    stockBefore,
    stockAfterPreview,
    canAfford,
    pricing,
  };
}

function buildItemPricingPreview(item, pricingContext) {
  const normalizedPricingContext = buildPricingContextWithModifiers(pricingContext);
  const buyBaseUnitPrice = Number(item.buy_price);
  const sellBaseUnitPrice = Number(item.sell_price);

  const currentBuyPrice = applyShopModifiers(
    calculateFinalUnitPrice(buyBaseUnitPrice, item.category, normalizedPricingContext),
    "buy",
    normalizedPricingContext.modifiers
  );
  const currentSellPrice = applyShopModifiers(
    calculateFinalUnitPrice(sellBaseUnitPrice, item.category, normalizedPricingContext),
    "sell",
    normalizedPricingContext.modifiers
  );
  const buyFee = applyTradeFeeModel("buy", currentBuyPrice);
  const sellFee = applyTradeFeeModel("sell", currentSellPrice);

  const pricingPreview = {
    buy: buildShopPreviewPricing({
      item,
      pricingContext: normalizedPricingContext,
      unitPrice: currentBuyPrice,
      quantity: 1,
      grossTotalPrice: buyFee.grossTotalPrice,
      feeRate: buyFee.feeRate,
      feeAmount: buyFee.feeAmount,
      totalPrice: buyFee.settlementTotalPrice,
      transactionType: "buy",
    }),
    sell: buildShopPreviewPricing({
      item,
      pricingContext: normalizedPricingContext,
      unitPrice: currentSellPrice,
      quantity: 1,
      grossTotalPrice: sellFee.grossTotalPrice,
      feeRate: sellFee.feeRate,
      feeAmount: sellFee.feeAmount,
      totalPrice: sellFee.settlementTotalPrice,
      transactionType: "sell",
    }),
  };

  return {
    currentBuyPrice,
    currentSellPrice,
    pricingPreview,
    activePricing: pricingPreview,
    pricingSummary: pricingPreview.buy.pricingSummary,
    pricingReasonTags: pricingPreview.buy.pricingReasonTags,
  };
}

function buildShopTransactionPayload({
  playerId,
  item,
  quantity,
  unitPrice,
  totalPrice,
  transactionType,
}) {
  assertValidTradeItem(item);
  if (transactionType !== "buy" && transactionType !== "sell") {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid transaction type");
  }

  return {
    playerId,
    itemId: item.item_id,
    category: item.category,
    quantity,
    unitPrice,
    totalPrice,
    transactionType,
  };
}

function createShopLedgerEntry({ playerId, totalPrice, transactionType }) {
  if (typeof playerId !== "string" || playerId.trim().length === 0) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "playerId is required");
  }
  if (!Number.isFinite(Number(totalPrice))) {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "totalPrice must be a finite number");
  }
  if (transactionType !== "buy" && transactionType !== "sell") {
    throw new ShopServiceError(shopErrorCode.INVALID_INPUT, "invalid transaction type");
  }

  if (transactionType === "buy") {
    return {
      playerId,
      type: "subtract",
      amount: totalPrice,
      reason: "shop_buy",
    };
  }

  return {
    playerId,
    type: "add",
    amount: totalPrice,
    reason: "shop_sell",
  };
}

function buildShopTradeResult({
  playerId,
  item,
  quantity,
  unitPrice,
  grossTotalPrice,
  feeRate,
  feeAmount,
  totalPrice,
  balanceAfter,
  pricing,
}) {
  const result = {
    playerId,
    itemId: item.item_id,
    quantity,
    unitPrice,
    grossTotalPrice,
    feeRate,
    feeAmount,
    netTotalPrice: totalPrice,
    totalPrice,
    balanceAfter: Number(balanceAfter),
    stockAfter: normalizeStockQuantity(item?.stock_quantity),
  };

  if (pricing) {
    result.pricing = pricing;
  }

  return result;
}

function mapItemRow(row) {
  validateItemCategory(row.category);

  return {
    itemId: row.item_id,
    itemName: row.item_name,
    category: row.category,
    buyPrice: Number(row.buy_price),
    sellPrice: Number(row.sell_price),
    isActive: row.is_active,
    stockQuantity: normalizeStockQuantity(row.stock_quantity),
    replenishAmount: Number(row.replenish_amount),
    replenishIntervalSeconds: Number(row.replenish_interval_seconds),
    lastReplenishedAt: row.last_replenished_at ?? null,
  };
}

async function getCurrentFocusRegion(executor) {
  const focusState = await findCurrentFocusState(executor);
  return focusState ? focusState.focus_region : null;
}

async function getCurrentActiveProjectEffects(executor) {
  // If a transaction client is provided, bypass cache to keep tx boundaries explicit.
  if (executor) {
    return getActiveProjectEffects(executor);
  }

  const now = Date.now();

  if (projectEffectsCache.rows && projectEffectsCache.expiresAt > now) {
    return projectEffectsCache.rows;
  }

  if (projectEffectsCache.inFlight) {
    return projectEffectsCache.inFlight;
  }

  projectEffectsCache.inFlight = getActiveProjectEffects(executor)
    .then((rows) => {
      projectEffectsCache.rows = rows;
      projectEffectsCache.expiresAt = Date.now() + PROJECT_EFFECTS_CACHE_TTL_MS;
      return rows;
    })
    .finally(() => {
      projectEffectsCache.inFlight = null;
    });

  return projectEffectsCache.inFlight;
}

async function getCurrentActiveEvents(executor) {
  return getActiveEventRows(executor);
}

async function getShopPricingContext(executor, itemCategory) {
  const [currentFocusRegion, activeProjectEffects, activeEvents, villageEffect] = await Promise.all([
    getCurrentFocusRegion(executor),
    getCurrentActiveProjectEffects(executor),
    getCurrentActiveEvents(executor),
    getVillageShopEffect(executor),
  ]);

  const modifiers = calculateShopModifiers({
    activeEvents,
    focusState: currentFocusRegion,
    projectEffects: activeProjectEffects,
    villageEffect,
    itemCategory,
  });

  return {
    currentFocusRegion,
    activeProjectEffects,
    activeEvents,
    villageEffect,
    modifiers,
  };
}

async function getRequiredItem(itemId, executor) {
  const item = await findShopItemByItemId(itemId, executor);
  if (!item) {
    throw new ShopServiceError(shopErrorCode.ITEM_NOT_FOUND, "item not found");
  }
  validateShopItemRow(item);
  return item;
}

async function loadTradeContext(client, itemId) {
  const loadedItem = await getRequiredItem(itemId, client);
  const item = await refreshShopItemStockIfNeeded(loadedItem, client);
  assertValidTradeItem(item);
  assertTradableItem(item);

  const pricingContext = await getShopPricingContext(client, item.category);
  assertValidPricingContext(pricingContext);

  return { item, pricingContext };
}

async function getWalletBalanceForPreview(playerId) {
  const wallet = await findWalletByPlayerId(playerId);
  if (!wallet) {
    return null;
  }
  return normalizeWalletBalance(wallet.balance);
}

function calculateFinalUnitPrice(basePrice, itemCategory, pricingContext) {
  void itemCategory;
  const normalizedPricingContext = normalizePricingContext(pricingContext);
  void normalizedPricingContext;
  return Number(basePrice);
}

export function calculateBuyPrice(item, quantity, pricingContext) {
  assertValidTradeItem(item);
  assertValidPricingContext(pricingContext);

  const normalizedPricingContext = buildPricingContextWithModifiers(pricingContext);
  const baseUnitPrice = Number(item.buy_price);
  const baseCalculatedUnitPrice = calculateFinalUnitPrice(
    baseUnitPrice,
    item.category,
    normalizedPricingContext
  );
  const modifiers = normalizedPricingContext.modifiers;
  const unitPrice = applyShopModifiers(baseCalculatedUnitPrice, "buy", modifiers);
  const grossTotalPrice = unitPrice * quantity;
  const fee = applyTradeFeeModel("buy", grossTotalPrice);
  const totalPrice = fee.settlementTotalPrice;

  validateUnitPriceAndTotalPrice(unitPrice, grossTotalPrice, "buy");
  validateSettlementTotalPrice(totalPrice, "buy");
  return {
    unitPrice,
    grossTotalPrice,
    feeRate: fee.feeRate,
    feeAmount: fee.feeAmount,
    totalPrice,
  };
}

export function calculateSellPrice(item, quantity, pricingContext) {
  assertValidTradeItem(item);
  assertValidPricingContext(pricingContext);

  const normalizedPricingContext = buildPricingContextWithModifiers(pricingContext);
  const baseUnitPrice = Number(item.sell_price);
  const baseCalculatedUnitPrice = calculateFinalUnitPrice(
    baseUnitPrice,
    item.category,
    normalizedPricingContext
  );
  const modifiers = normalizedPricingContext.modifiers;
  const unitPrice = applyShopModifiers(baseCalculatedUnitPrice, "sell", modifiers);
  const grossTotalPrice = unitPrice * quantity;
  const fee = applyTradeFeeModel("sell", grossTotalPrice);
  const totalPrice = fee.settlementTotalPrice;

  validateUnitPriceAndTotalPrice(unitPrice, grossTotalPrice, "sell");
  validateSettlementTotalPrice(totalPrice, "sell");
  return {
    unitPrice,
    grossTotalPrice,
    feeRate: fee.feeRate,
    feeAmount: fee.feeAmount,
    totalPrice,
  };
}

export async function listShopItems() {
  const rows = await findAllShopItems();
  const pricingContextByCategory = new Map();

  const loadPricingContextByCategory = (category) => {
    if (!pricingContextByCategory.has(category)) {
      pricingContextByCategory.set(category, getShopPricingContext(undefined, category));
    }
    return pricingContextByCategory.get(category);
  };

  return Promise.all(
    rows.map(async (row) => {
      const refreshedRow = await refreshShopItemStockIfNeeded(row);
      validateShopItemRow(refreshedRow);
      const mappedItem = mapItemRow(refreshedRow);
      try {
        const pricingContext = await loadPricingContextByCategory(refreshedRow.category);
        const preview = buildItemPricingPreview(refreshedRow, pricingContext);
        return {
          ...mappedItem,
          ...preview,
        };
      } catch (_error) {
        return mappedItem;
      }
    })
  );
}

export async function getShopItem(itemId) {
  validateItemId(itemId);

  const loadedItem = await findShopItemByItemId(itemId);
  if (!loadedItem) {
    throw new ShopServiceError(shopErrorCode.ITEM_NOT_FOUND, "item not found");
  }
  const item = await refreshShopItemStockIfNeeded(loadedItem);

  validateShopItemRow(item);
  const mappedItem = mapItemRow(item);

  try {
    const pricingContext = await getShopPricingContext(undefined, item.category);
    const preview = buildItemPricingPreview(item, pricingContext);
    return {
      ...mappedItem,
      ...preview,
    };
  } catch (_error) {
    return mappedItem;
  }
}

export async function previewBuyItem({ playerId, itemId, quantity }) {
  validateUuid(playerId, "playerId");
  validateItemId(itemId);
  validateQuantity(quantity);

  const { item, pricingContext } = await loadTradeContext(undefined, itemId);
  assertBuyStockAvailable(item, quantity);
  const { unitPrice, grossTotalPrice, feeRate, feeAmount, totalPrice } = calculateBuyPrice(
    item,
    quantity,
    pricingContext
  );
  const pricing = buildShopPreviewPricing({
    item,
    pricingContext,
    unitPrice,
    quantity,
    grossTotalPrice,
    feeRate,
    feeAmount,
    totalPrice,
    transactionType: "buy",
  });
  const balanceBefore = await getWalletBalanceForPreview(playerId);

  return buildShopPreviewResult({
    transactionType: "buy",
    playerId,
    item,
    quantity,
    unitPrice,
    grossTotalPrice,
    feeRate,
    feeAmount,
    totalPrice,
    pricing,
    balanceBefore,
  });
}

export async function previewSellItem({ playerId, itemId, quantity }) {
  validateUuid(playerId, "playerId");
  validateItemId(itemId);
  validateQuantity(quantity);
  validateSellQuantityLimit(quantity);

  const { item, pricingContext } = await loadTradeContext(undefined, itemId);
  const { unitPrice, grossTotalPrice, feeRate, feeAmount, totalPrice } = calculateSellPrice(
    item,
    quantity,
    pricingContext
  );
  const pricing = buildShopPreviewPricing({
    item,
    pricingContext,
    unitPrice,
    quantity,
    grossTotalPrice,
    feeRate,
    feeAmount,
    totalPrice,
    transactionType: "sell",
  });
  const balanceBefore = await getWalletBalanceForPreview(playerId);

  return buildShopPreviewResult({
    transactionType: "sell",
    playerId,
    item,
    quantity,
    unitPrice,
    grossTotalPrice,
    feeRate,
    feeAmount,
    totalPrice,
    pricing,
    balanceBefore,
  });
}

export async function buyItem({ playerId, itemId, quantity }) {
  validateUuid(playerId, "playerId");
  validateItemId(itemId);
  validateQuantity(quantity);

  return withTransaction(async (client) => {
    const { item, pricingContext } = await loadTradeContext(client, itemId);
    const { unitPrice, grossTotalPrice, feeRate, feeAmount, totalPrice } = calculateBuyPrice(
      item,
      quantity,
      pricingContext
    );
    const pricing = buildShopPreviewPricing({
      item,
      pricingContext,
      unitPrice,
      quantity,
      grossTotalPrice,
      feeRate,
      feeAmount,
      totalPrice,
      transactionType: "buy",
    });

    await ensurePlayerExists(playerId, client);
    await ensureWalletExists(playerId, client);
    await assertNoRapidFlipTrade(playerId, item.item_id, "buy", client);

    const stockedItem = await decreaseShopItemStockIfEnough(item.item_id, quantity, client);
    if (!stockedItem) {
      throw new ShopServiceError(shopErrorCode.INSUFFICIENT_STOCK, "insufficient stock");
    }

    const updatedWallet = await subtractBalanceIfEnough(playerId, totalPrice, client);
    if (!updatedWallet) {
      throw new ShopServiceError(shopErrorCode.INSUFFICIENT_BALANCE, "insufficient balance");
    }

    const ledgerEntry = createShopLedgerEntry({
      playerId,
      totalPrice,
      transactionType: "buy",
    });
    await insertLedgerEntry(ledgerEntry, client);

    const shopTransactionPayload = buildShopTransactionPayload({
      playerId,
      item,
      quantity,
      unitPrice,
      totalPrice,
      transactionType: "buy",
    });
    await insertShopTransaction(shopTransactionPayload, client);

    return buildShopTradeResult({
      playerId,
      item: stockedItem,
      quantity,
      unitPrice,
      grossTotalPrice,
      feeRate,
      feeAmount,
      totalPrice,
      balanceAfter: updatedWallet.balance,
      pricing,
    });
  });
}

export async function sellItem({ playerId, itemId, quantity }) {
  validateUuid(playerId, "playerId");
  validateItemId(itemId);
  validateQuantity(quantity);
  validateSellQuantityLimit(quantity);

  return withTransaction(async (client) => {
    const { item, pricingContext } = await loadTradeContext(client, itemId);
    const { unitPrice, grossTotalPrice, feeRate, feeAmount, totalPrice } = calculateSellPrice(
      item,
      quantity,
      pricingContext
    );
    const pricing = buildShopPreviewPricing({
      item,
      pricingContext,
      unitPrice,
      quantity,
      grossTotalPrice,
      feeRate,
      feeAmount,
      totalPrice,
      transactionType: "sell",
    });

    await ensurePlayerExists(playerId, client);
    await ensureWalletExists(playerId, client);
    await assertNoRapidFlipTrade(playerId, item.item_id, "sell", client);

    const updatedWallet = await addBalance(playerId, totalPrice, client);
    const stockedItem = await increaseShopItemStock(item.item_id, quantity, client);
    if (!stockedItem) {
      throw new ShopServiceError(shopErrorCode.ITEM_NOT_FOUND, "item not found");
    }

    const ledgerEntry = createShopLedgerEntry({
      playerId,
      totalPrice,
      transactionType: "sell",
    });
    await insertLedgerEntry(ledgerEntry, client);

    const shopTransactionPayload = buildShopTransactionPayload({
      playerId,
      item,
      quantity,
      unitPrice,
      totalPrice,
      transactionType: "sell",
    });
    await insertShopTransaction(shopTransactionPayload, client);

    return buildShopTradeResult({
      playerId,
      item: stockedItem,
      quantity,
      unitPrice,
      grossTotalPrice,
      feeRate,
      feeAmount,
      totalPrice,
      balanceAfter: updatedWallet.balance,
      pricing,
    });
  });
}
