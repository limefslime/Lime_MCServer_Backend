import { withTransaction } from "../../db/pool.js";
import {
  addBalance,
  ensurePlayerExists,
  ensureWalletExists,
  findWalletByPlayerId,
  insertLedgerEntry,
  subtractBalanceIfEnough,
} from "./wallet.repository.js";

class WalletServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "WalletServiceError";
    this.code = code;
  }
}

export const walletErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  INSUFFICIENT_BALANCE: "INSUFFICIENT_BALANCE",
};

const ledgerReasonList = new Set([
  "shop_buy",
  "shop_sell",
  "event_reward",
  "admin",
  "mail_transfer",
  "quest_reward",
  "shop_purchase",
  "invest_project",
  "mail_reward",
]);

export function isWalletServiceError(error) {
  return error instanceof WalletServiceError;
}

function validatePlayerId(playerId) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof playerId !== "string" || !uuidRegex.test(playerId)) {
    throw new WalletServiceError(walletErrorCode.INVALID_INPUT, "playerId must be a valid uuid");
  }
}

function validateAmount(amount) {
  if (!Number.isInteger(amount) || amount <= 0) {
    throw new WalletServiceError(walletErrorCode.INVALID_INPUT, "amount must be a positive integer");
  }
}

function validateLedgerReason(reason) {
  if (typeof reason !== "string" || reason.trim().length === 0) {
    throw new WalletServiceError(walletErrorCode.INVALID_INPUT, "reason is required");
  }
  if (!ledgerReasonList.has(reason)) {
    throw new WalletServiceError(
      walletErrorCode.INVALID_INPUT,
      `reason must be one of: ${Array.from(ledgerReasonList).join(", ")}`
    );
  }
}

function assertWalletRow(walletRow) {
  const isObject = typeof walletRow === "object" && walletRow !== null;
  const numericBalance = isObject ? Number(walletRow.balance) : NaN;

  if (!isObject || !Number.isFinite(numericBalance)) {
    throw new Error("invalid wallet row");
  }
}

function buildLedgerEntryPayload({ playerId, type, amount, reason }) {
  return {
    playerId,
    type,
    amount,
    reason,
  };
}

function buildWalletBalanceResult(walletRow) {
  assertWalletRow(walletRow);

  return {
    balance: Number(walletRow.balance),
  };
}

async function ensurePlayerAndWallet(playerId, executor) {
  await ensurePlayerExists(playerId, executor);
  await ensureWalletExists(playerId, executor);
}

export async function getWallet(playerId) {
  validatePlayerId(playerId);

  // Read path stays non-transactional. Write APIs use transactions.
  await ensurePlayerAndWallet(playerId);

  const wallet = await findWalletByPlayerId(playerId);
  if (!wallet) {
    return {
      playerId,
      balance: 0,
    };
  }

  return {
    playerId,
    ...buildWalletBalanceResult(wallet),
  };
}

export async function addMoney({ playerId, amount, reason }) {
  validatePlayerId(playerId);
  validateAmount(amount);
  validateLedgerReason(reason);

  return withTransaction(async (client) => {
    await ensurePlayerAndWallet(playerId, client);

    const updatedWallet = await addBalance(playerId, amount, client);
    assertWalletRow(updatedWallet);

    const ledgerEntry = buildLedgerEntryPayload({
      playerId,
      type: "add",
      amount,
      reason,
    });
    await insertLedgerEntry(ledgerEntry, client);

    return {
      playerId,
      ...buildWalletBalanceResult(updatedWallet),
    };
  });
}

export async function subtractMoney({ playerId, amount, reason }) {
  validatePlayerId(playerId);
  validateAmount(amount);
  validateLedgerReason(reason);

  return withTransaction(async (client) => {
    await ensurePlayerAndWallet(playerId, client);

    const updatedWallet = await subtractBalanceIfEnough(playerId, amount, client);
    if (!updatedWallet) {
      throw new WalletServiceError(walletErrorCode.INSUFFICIENT_BALANCE, "insufficient balance");
    }
    assertWalletRow(updatedWallet);

    const ledgerEntry = buildLedgerEntryPayload({
      playerId,
      type: "subtract",
      amount,
      reason,
    });
    await insertLedgerEntry(ledgerEntry, client);

    return {
      playerId,
      ...buildWalletBalanceResult(updatedWallet),
    };
  });
}
