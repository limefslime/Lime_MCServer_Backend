import { withTransaction } from "../../db/pool.js";
import {
  findAllStocks,
  findStockByIdForUpdate,
  insertStockPriceHistory,
  updateStockPrice,
} from "./stock.repository.js";

const VOLATILITY_RATE_BY_TYPE = {
  stable: 0.02,
  normal: 0.06,
  high: 0.12,
};

function normalizeVolatilityType(rawType) {
  if (rawType === "stable" || rawType === "normal" || rawType === "high") {
    return rawType;
  }
  return "normal";
}

function clampInt(value, min, max) {
  const safeMin = Number.isFinite(min) ? Math.floor(min) : 1;
  const safeMax = Number.isFinite(max) ? Math.floor(max) : safeMin;
  if (safeMax <= safeMin) {
    return safeMin;
  }
  const safeValue = Number.isFinite(value) ? Math.floor(value) : safeMin;
  return Math.max(safeMin, Math.min(safeValue, safeMax));
}

function resolveVolatilityRate(volatilityType) {
  const normalizedType = normalizeVolatilityType(volatilityType);
  return VOLATILITY_RATE_BY_TYPE[normalizedType] ?? VOLATILITY_RATE_BY_TYPE.normal;
}

function randomBetween(min, max, randomFn = Math.random) {
  const safeRandom = typeof randomFn === "function" ? randomFn() : Math.random();
  return min + (max - min) * safeRandom;
}

function resolveNextPrice(currentPrice, volatilityType, minPrice, maxPrice, randomFn = Math.random) {
  const rate = resolveVolatilityRate(volatilityType);
  const changeRate = randomBetween(-rate, rate, randomFn);
  const rawNext = Math.round(currentPrice * (1 + changeRate));
  const minimumStep = changeRate >= 0 ? 1 : -1;
  const normalizedNext =
    rawNext === currentPrice ? currentPrice + minimumStep : rawNext;
  return clampInt(normalizedNext, minPrice, maxPrice);
}

function mapUpdatedStockRow(row) {
  const currentPrice = Number(row.current_price);
  const previousPrice = Number(row.previous_price);
  const changeAmount = currentPrice - previousPrice;
  const changeRate = previousPrice > 0 ? changeAmount / previousPrice : 0;

  return {
    id: row.id,
    name: row.name,
    currentPrice,
    previousPrice,
    changeAmount,
    changeRate,
    volatilityType: row.volatility_type,
    minPrice: Number(row.min_price),
    maxPrice: Number(row.max_price),
  };
}

export async function updateStockPrices({ randomFn } = {}) {
  return withTransaction(async (client) => {
    const rows = await findAllStocks(client);
    const updatedRows = [];

    for (const row of rows) {
      const locked = await findStockByIdForUpdate(row.id, client);
      if (!locked) {
        continue;
      }

      const currentPrice = Number(locked.current_price);
      const minPrice = Number(locked.min_price);
      const maxPrice = Number(locked.max_price);

      const nextPrice = resolveNextPrice(
        currentPrice,
        locked.volatility_type,
        minPrice,
        maxPrice,
        randomFn
      );

      const updated = await updateStockPrice(
        {
          stockId: locked.id,
          previousPrice: currentPrice,
          currentPrice: nextPrice,
        },
        client
      );
      if (!updated) {
        continue;
      }

      await insertStockPriceHistory(
        {
          stockId: updated.id,
          price: Number(updated.current_price),
        },
        client
      );
      updatedRows.push(mapUpdatedStockRow(updated));
    }

    return updatedRows;
  });
}

export function testOnlyResolveNextPrice({
  currentPrice,
  volatilityType,
  minPrice,
  maxPrice,
  randomFn,
}) {
  return resolveNextPrice(currentPrice, volatilityType, minPrice, maxPrice, randomFn);
}
