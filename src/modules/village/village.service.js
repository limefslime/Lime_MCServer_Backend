import { withTransaction } from "../../db/pool.js";
import {
  ensurePlayerExists,
  ensureWalletExists,
  findWalletByPlayerId,
  insertLedgerEntry,
  subtractBalanceIfEnough,
} from "../wallet/wallet.repository.js";
import {
  addPlayerContribution,
  ensureVillageFundRow,
  getPlayerContribution,
  getVillageFund,
  getVillageFundForUpdate,
  updateVillageFund,
} from "./village.repository.js";

class VillageServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "VillageServiceError";
    this.code = code;
  }
}

export const villageErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  INSUFFICIENT_BALANCE: "INSUFFICIENT_BALANCE",
};

const BASE_LEVEL_REQUIREMENT = 10000;
const MAX_SHOP_DISCOUNT_RATE = 0.15;

export function isVillageServiceError(error) {
  return error instanceof VillageServiceError;
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throw new VillageServiceError(villageErrorCode.INVALID_INPUT, `${fieldName} must be a valid uuid`);
  }
}

function validateAmount(amount) {
  if (!Number.isInteger(amount) || amount <= 0) {
    throw new VillageServiceError(villageErrorCode.INVALID_INPUT, "amount must be a positive integer");
  }
}

function resolveNextLevelRequirement(level) {
  const safeLevel = Math.max(1, Math.floor(Number(level) || 1));
  return BASE_LEVEL_REQUIREMENT * safeLevel;
}

function toInt(value, fallback = 0) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  return Math.round(numeric);
}

function getVillageDiscountRateByLevel(level) {
  const safeLevel = Math.max(1, Math.floor(Number(level) || 1));
  return Math.min(MAX_SHOP_DISCOUNT_RATE, safeLevel * 0.01);
}

function mapVillageFundRow(row) {
  const level = Math.max(1, toInt(row?.level, 1));
  const totalAmount = Math.max(0, toInt(row?.total_amount, 0));
  const nextLevelRequirement = Math.max(1, toInt(row?.next_level_requirement, resolveNextLevelRequirement(level)));
  const remainingToNextLevel = Math.max(0, nextLevelRequirement - totalAmount);
  const shopDiscountRate = getVillageDiscountRateByLevel(level);

  return {
    totalAmount,
    level,
    nextLevelRequirement,
    remainingToNextLevel,
    shopDiscountRate,
  };
}

function mapContributionRow(row) {
  return {
    totalContribution: Math.max(0, toInt(row?.total_contribution, 0)),
  };
}

async function ensureVillageReady(executor) {
  await ensureVillageFundRow(executor);
}

async function ensurePlayerReady(playerId, executor) {
  await ensurePlayerExists(playerId, executor);
  await ensureWalletExists(playerId, executor);
}

export async function getVillageFundStatus({ playerId }) {
  validateUuid(playerId, "playerId");
  await ensureVillageReady();
  await ensurePlayerReady(playerId);

  const [fundRow, contributionRow, walletRow] = await Promise.all([
    getVillageFund(),
    getPlayerContribution(playerId),
    findWalletByPlayerId(playerId),
  ]);

  const villageFund = mapVillageFundRow(fundRow);
  const contribution = mapContributionRow(contributionRow);

  return {
    villageFund,
    contribution,
    wallet: {
      balance: Math.max(0, toInt(walletRow?.balance, 0)),
    },
  };
}

export async function donateVillageFund({ playerId, amount }) {
  validateUuid(playerId, "playerId");
  validateAmount(amount);

  return withTransaction(async (client) => {
    await ensureVillageReady(client);
    await ensurePlayerReady(playerId, client);

    const walletAfter = await subtractBalanceIfEnough(playerId, amount, client);
    if (!walletAfter) {
      throw new VillageServiceError(villageErrorCode.INSUFFICIENT_BALANCE, "insufficient balance");
    }

    await insertLedgerEntry(
      {
        playerId,
        type: "subtract",
        amount,
        reason: "village_donation",
      },
      client
    );

    const currentFund = await getVillageFundForUpdate(client);
    const safeCurrent = mapVillageFundRow(currentFund);
    const nextTotalAmount = safeCurrent.totalAmount + amount;

    let level = safeCurrent.level;
    let nextRequirement = safeCurrent.nextLevelRequirement;
    while (nextTotalAmount >= nextRequirement) {
      level += 1;
      nextRequirement = resolveNextLevelRequirement(level);
    }

    const updatedFundRow = await updateVillageFund(
      {
        totalAmount: nextTotalAmount,
        level,
        nextLevelRequirement: nextRequirement,
      },
      client
    );
    const updatedContributionRow = await addPlayerContribution(playerId, amount, client);

    return {
      donatedAmount: amount,
      villageFund: mapVillageFundRow(updatedFundRow),
      contribution: mapContributionRow(updatedContributionRow),
      wallet: {
        balance: Math.max(0, toInt(walletAfter.balance, 0)),
      },
    };
  });
}

export async function getVillageShopEffect(executor) {
  await ensureVillageReady(executor);
  const fundRow = await getVillageFund(executor);
  const fund = mapVillageFundRow(fundRow);
  const discountRate = fund.shopDiscountRate;
  const buyPriceMultiplier = Math.max(0.5, 1 - discountRate);

  return {
    applied: discountRate > 0,
    level: fund.level,
    discountRate,
    buyPriceMultiplier,
  };
}
