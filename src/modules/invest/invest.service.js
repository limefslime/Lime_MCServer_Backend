import { withTransaction } from "../../db/pool.js";
import {
  addBalance,
  ensurePlayerExists,
  ensureWalletExists,
  findWalletByPlayerId,
  insertLedgerEntry,
  subtractBalanceIfEnough,
} from "../wallet/wallet.repository.js";
import {
  deletePlayerStockPosition,
  findPlayerStockPosition,
  findPlayerStockPositionForUpdate,
  findStockById,
  findStockByIdForUpdate,
  findAllStocks,
  listStocksWithPlayerPosition,
  upsertPlayerStockPosition,
} from "./stock.repository.js";

class InvestServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "InvestServiceError";
    this.code = code;
  }
}

export const investErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  PROJECT_NOT_FOUND: "PROJECT_NOT_FOUND",
  PROJECT_NOT_ACTIVE: "PROJECT_NOT_ACTIVE",
  INSUFFICIENT_BALANCE: "INSUFFICIENT_BALANCE",
  SERVICE_DISABLED: "SERVICE_DISABLED",
  STOCK_NOT_FOUND: "STOCK_NOT_FOUND",
  INSUFFICIENT_QUANTITY: "INSUFFICIENT_QUANTITY",
};

const LEGACY_DISABLED_MESSAGE = "project-based invest system is disabled";
const MAX_SAFE_TOTAL = Number.MAX_SAFE_INTEGER;

export function isInvestServiceError(error) {
  return error instanceof InvestServiceError;
}

function throwInvalid(message) {
  throw new InvestServiceError(investErrorCode.INVALID_INPUT, message);
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throwInvalid(`${fieldName} must be a valid uuid`);
  }
}

function validatePlayerId(playerId) {
  validateUuid(playerId, "playerId");
}

function validateStockId(stockId) {
  validateUuid(stockId, "stockId");
}

function validateQuantity(quantity) {
  if (!Number.isInteger(quantity) || quantity <= 0) {
    throwInvalid("quantity must be a positive integer");
  }
}

function ensureSafeTotal(unitPrice, quantity) {
  const total = Number(unitPrice) * Number(quantity);
  if (!Number.isSafeInteger(total) || total <= 0 || total > MAX_SAFE_TOTAL) {
    throwInvalid("trade total is too large");
  }
  return total;
}

function toInt(value, fallback = 0) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  return Math.round(numeric);
}

export function calculateEvaluation({ quantity, avgBuyPrice, currentPrice }) {
  const safeQuantity = Math.max(0, toInt(quantity, 0));
  const safeAvgBuyPrice = Math.max(0, toInt(avgBuyPrice, 0));
  const safeCurrentPrice = Math.max(0, toInt(currentPrice, 0));

  const investedCost = safeQuantity * safeAvgBuyPrice;
  const marketValue = safeQuantity * safeCurrentPrice;
  const unrealizedPnl = marketValue - investedCost;

  return {
    quantity: safeQuantity,
    avgBuyPrice: safeAvgBuyPrice,
    currentPrice: safeCurrentPrice,
    investedCost,
    marketValue,
    unrealizedPnl,
  };
}

function mapStockRow(row, position = null) {
  const currentPrice = toInt(row.current_price, 0);
  const previousPrice = toInt(row.previous_price, currentPrice);
  const quantity = Math.max(0, toInt(position?.quantity, 0));
  const avgBuyPrice = Math.max(0, toInt(position?.avg_buy_price, 0));
  const evaluation = calculateEvaluation({
    quantity,
    avgBuyPrice,
    currentPrice,
  });
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
    minPrice: toInt(row.min_price, currentPrice),
    maxPrice: toInt(row.max_price, currentPrice),
    holding: {
      quantity: evaluation.quantity,
      avgBuyPrice: evaluation.avgBuyPrice,
      investedCost: evaluation.investedCost,
      marketValue: evaluation.marketValue,
      unrealizedPnl: evaluation.unrealizedPnl,
    },
  };
}

function mapWalletRow(walletRow) {
  return {
    balance: Math.max(0, toInt(walletRow?.balance, 0)),
  };
}

async function ensureTraderReady(playerId, executor) {
  await ensurePlayerExists(playerId, executor);
  await ensureWalletExists(playerId, executor);
}

async function getStockOrThrow(stockId, executor) {
  const stock = await findStockById(stockId, executor);
  if (!stock) {
    throw new InvestServiceError(investErrorCode.STOCK_NOT_FOUND, "stock not found");
  }
  return stock;
}

async function getStockForTradeOrThrow(stockId, executor) {
  const stock = await findStockByIdForUpdate(stockId, executor);
  if (!stock) {
    throw new InvestServiceError(investErrorCode.STOCK_NOT_FOUND, "stock not found");
  }
  return stock;
}

async function getWalletSnapshot(playerId, executor) {
  await ensureTraderReady(playerId, executor);
  const wallet = await findWalletByPlayerId(playerId, executor);
  return mapWalletRow(wallet);
}

function buildTradeResult({
  side,
  stock,
  quantity,
  executedPrice,
  totalPrice,
  walletBalanceAfter,
  holding,
  realizedPnl = 0,
}) {
  return {
    side,
    stockId: stock.id,
    stockName: stock.name,
    quantity,
    executedPrice,
    totalPrice,
    walletBalanceAfter,
    realizedPnl,
    holding,
    stock: mapStockRow(stock, {
      quantity: holding.quantity,
      avg_buy_price: holding.avgBuyPrice,
    }),
  };
}

function calculateNextAverageBuyPrice({
  currentQuantity,
  currentAvgBuyPrice,
  buyQuantity,
  buyPrice,
}) {
  const nextQuantity = currentQuantity + buyQuantity;
  if (nextQuantity <= 0) {
    return 0;
  }

  const weightedCost = currentQuantity * currentAvgBuyPrice + buyQuantity * buyPrice;
  return Math.round(weightedCost / nextQuantity);
}

function toHoldingPayload(position, currentPrice) {
  return calculateEvaluation({
    quantity: position?.quantity ?? 0,
    avgBuyPrice: position?.avg_buy_price ?? 0,
    currentPrice,
  });
}

export async function listStocks({ playerId = null } = {}) {
  if (playerId == null) {
    const rows = await findAllStocks();
    return {
      stocks: rows.map((row) => mapStockRow(row)),
    };
  }

  validatePlayerId(playerId);
  await ensureTraderReady(playerId);
  const rows = await listStocksWithPlayerPosition(playerId);
  return {
    stocks: rows.map((row) =>
      mapStockRow(row, {
        quantity: row.quantity,
        avg_buy_price: row.avg_buy_price,
      })
    ),
  };
}

export async function getStockDetail(stockId, { playerId = null } = {}) {
  validateStockId(stockId);
  const stock = await getStockOrThrow(stockId);

  if (playerId == null) {
    return {
      stock: mapStockRow(stock),
      wallet: null,
    };
  }

  validatePlayerId(playerId);
  await ensureTraderReady(playerId);
  const [position, wallet] = await Promise.all([
    findPlayerStockPosition(playerId, stockId),
    getWalletSnapshot(playerId),
  ]);

  return {
    stock: mapStockRow(stock, position),
    wallet,
  };
}

export async function buyStock({ playerId, stockId, quantity }) {
  validatePlayerId(playerId);
  validateStockId(stockId);
  validateQuantity(quantity);

  return withTransaction(async (client) => {
    await ensureTraderReady(playerId, client);

    const stock = await getStockForTradeOrThrow(stockId, client);
    const executedPrice = toInt(stock.current_price, 0);
    const totalPrice = ensureSafeTotal(executedPrice, quantity);

    const walletAfter = await subtractBalanceIfEnough(playerId, totalPrice, client);
    if (!walletAfter) {
      throw new InvestServiceError(investErrorCode.INSUFFICIENT_BALANCE, "insufficient balance");
    }

    const currentPosition = await findPlayerStockPositionForUpdate(playerId, stockId, client);
    const currentQuantity = Math.max(0, toInt(currentPosition?.quantity, 0));
    const currentAvgBuyPrice = Math.max(0, toInt(currentPosition?.avg_buy_price, 0));
    const nextQuantity = currentQuantity + quantity;
    const nextAvgBuyPrice = calculateNextAverageBuyPrice({
      currentQuantity,
      currentAvgBuyPrice,
      buyQuantity: quantity,
      buyPrice: executedPrice,
    });

    const updatedPosition = await upsertPlayerStockPosition(
      {
        playerId,
        stockId,
        quantity: nextQuantity,
        avgBuyPrice: nextAvgBuyPrice,
      },
      client
    );

    await insertLedgerEntry(
      {
        playerId,
        type: "subtract",
        amount: totalPrice,
        reason: "invest_stock_buy",
      },
      client
    );

    const holding = toHoldingPayload(updatedPosition, executedPrice);
    return buildTradeResult({
      side: "buy",
      stock,
      quantity,
      executedPrice,
      totalPrice,
      walletBalanceAfter: mapWalletRow(walletAfter).balance,
      holding,
      realizedPnl: 0,
    });
  });
}

export async function sellStock({ playerId, stockId, quantity }) {
  validatePlayerId(playerId);
  validateStockId(stockId);
  validateQuantity(quantity);

  return withTransaction(async (client) => {
    await ensureTraderReady(playerId, client);

    const stock = await getStockForTradeOrThrow(stockId, client);
    const executedPrice = toInt(stock.current_price, 0);
    const totalPrice = ensureSafeTotal(executedPrice, quantity);

    const currentPosition = await findPlayerStockPositionForUpdate(playerId, stockId, client);
    const currentQuantity = Math.max(0, toInt(currentPosition?.quantity, 0));
    const currentAvgBuyPrice = Math.max(0, toInt(currentPosition?.avg_buy_price, 0));
    if (currentQuantity < quantity) {
      throw new InvestServiceError(
        investErrorCode.INSUFFICIENT_QUANTITY,
        "insufficient stock quantity"
      );
    }

    const nextQuantity = currentQuantity - quantity;
    const realizedPnl = (executedPrice - currentAvgBuyPrice) * quantity;

    if (nextQuantity <= 0) {
      await deletePlayerStockPosition(playerId, stockId, client);
    } else {
      await upsertPlayerStockPosition(
        {
          playerId,
          stockId,
          quantity: nextQuantity,
          avgBuyPrice: currentAvgBuyPrice,
        },
        client
      );
    }

    const walletAfter = await addBalance(playerId, totalPrice, client);
    await insertLedgerEntry(
      {
        playerId,
        type: "add",
        amount: totalPrice,
        reason: "invest_stock_sell",
      },
      client
    );

    const holding = calculateEvaluation({
      quantity: nextQuantity,
      avgBuyPrice: nextQuantity <= 0 ? 0 : currentAvgBuyPrice,
      currentPrice: executedPrice,
    });
    return buildTradeResult({
      side: "sell",
      stock,
      quantity,
      executedPrice,
      totalPrice,
      walletBalanceAfter: mapWalletRow(walletAfter).balance,
      holding,
      realizedPnl,
    });
  });
}

// Legacy project-based APIs are intentionally kept but disabled.
export async function createProject(_payload) {
  throw new InvestServiceError(investErrorCode.SERVICE_DISABLED, LEGACY_DISABLED_MESSAGE);
}

export async function getProjects() {
  return [];
}

export async function getProjectDetail(_projectId) {
  throw new InvestServiceError(investErrorCode.SERVICE_DISABLED, LEGACY_DISABLED_MESSAGE);
}

export async function investToProject(_projectId, _payload) {
  throw new InvestServiceError(investErrorCode.SERVICE_DISABLED, LEGACY_DISABLED_MESSAGE);
}

export async function getProjectProgress(_projectId) {
  throw new InvestServiceError(investErrorCode.SERVICE_DISABLED, LEGACY_DISABLED_MESSAGE);
}
