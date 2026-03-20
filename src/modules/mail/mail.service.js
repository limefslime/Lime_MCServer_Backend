import { withTransaction } from "../../db/pool.js";
import {
  addBalance,
  ensurePlayerExists,
  ensureWalletExists,
  findWalletByPlayerId,
  insertLedgerEntry,
} from "../wallet/wallet.repository.js";
import {
  countAllMails,
  countAllProjectCompletionMails,
  countAllProjectRewardMails,
  countPlayerProjectRewardMails,
  countProjectRewardMails,
  countUnclaimedRewardMails,
  createMail,
  findPlayerProjectCompletionMails,
  findPlayerProjectRewardMails,
  findProjectCompletionMail,
  findProjectCompletionMails,
  findProjectRewardMails,
  getMailById,
  getPlayerMails,
  markMailClaimed,
  sumPlayerProjectRewardAmounts,
  sumProjectRewardAmounts,
} from "./mail.repository.js";

class MailServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "MailServiceError";
    this.code = code;
  }
}

export const mailErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  MAIL_NOT_FOUND: "MAIL_NOT_FOUND",
  MAIL_ALREADY_CLAIMED: "MAIL_ALREADY_CLAIMED",
};

const MAX_INT_32 = 2147483647;
const ITEM_REWARD_TAG_PREFIX = "[item_reward:";
const ITEM_REWARD_TAG_REGEX = /\[item_reward:([^\]]+)\]/i;

export function isMailServiceError(error) {
  return error instanceof MailServiceError;
}

function validatePlayerId(playerId) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof playerId !== "string" || !uuidRegex.test(playerId)) {
    throw new MailServiceError(mailErrorCode.INVALID_INPUT, "playerId must be a valid uuid");
  }
}

function validateMailId(mailId) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof mailId !== "string" || !uuidRegex.test(mailId)) {
    throw new MailServiceError(mailErrorCode.INVALID_INPUT, "mailId must be a valid uuid");
  }
}

function validateProjectId(projectId) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof projectId !== "string" || !uuidRegex.test(projectId)) {
    throw new MailServiceError(mailErrorCode.INVALID_INPUT, "projectId must be a valid uuid");
  }
}

function validateRewardAmount(rewardAmount) {
  if (!Number.isInteger(rewardAmount) || rewardAmount < 0 || rewardAmount > MAX_INT_32) {
    throw new MailServiceError(
      mailErrorCode.INVALID_INPUT,
      `rewardAmount must be an integer between 0 and ${MAX_INT_32}`
    );
  }
}

function validateTitle(title) {
  if (typeof title !== "string" || title.trim().length === 0) {
    throw new MailServiceError(mailErrorCode.INVALID_INPUT, "title is required");
  }
}

function validateItemReward(itemReward) {
  if (itemReward == null) {
    return;
  }

  const isObject = typeof itemReward === "object" && itemReward !== null;
  const hasItemId =
    isObject && typeof itemReward.itemId === "string" && itemReward.itemId.trim().length > 0;
  const hasQuantity = isObject && Number.isInteger(itemReward.quantity) && itemReward.quantity > 0;

  if (!isObject || !hasItemId || !hasQuantity) {
    throw new MailServiceError(
      mailErrorCode.INVALID_INPUT,
      "itemReward must include itemId and positive integer quantity"
    );
  }
}

function validateSendMailPayload({ playerId, title, rewardAmount, itemReward }) {
  validatePlayerId(playerId);
  validateTitle(title);
  validateRewardAmount(rewardAmount);
  validateItemReward(itemReward);
}

function normalizeRewardAmount(value) {
  return Number(value);
}

function assertMailRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasPlayerId =
    isObject && typeof row.player_id === "string" && row.player_id.trim().length > 0;
  const hasTitle = isObject && typeof row.title === "string";
  const hasIsClaimed = isObject && typeof row.is_claimed === "boolean";
  const hasRewardAmount = isObject && Number.isFinite(normalizeRewardAmount(row.reward_amount));

  if (!isObject || !hasId || !hasPlayerId || !hasTitle || !hasIsClaimed || !hasRewardAmount) {
    throw new Error("invalid mail row");
  }
}

function buildMailLedgerEntry({ playerId, targetPlayerId = null, type, amount, reason }) {
  return {
    playerId,
    targetPlayerId,
    type,
    amount,
    reason,
  };
}

function extractItemRewardFromMessage(message) {
  if (typeof message !== "string" || message.trim().length === 0) {
    return null;
  }

  const matched = message.match(ITEM_REWARD_TAG_REGEX);
  if (!matched || typeof matched[1] !== "string") {
    return null;
  }

  try {
    const decoded = decodeURIComponent(matched[1]);
    const parsed = JSON.parse(decoded);
    validateItemReward(parsed);
    return {
      itemId: parsed.itemId,
      quantity: parsed.quantity,
    };
  } catch (_error) {
    return null;
  }
}

function hasItemRewardTag(message) {
  return typeof message === "string" && message.includes(ITEM_REWARD_TAG_PREFIX);
}

function encodeItemRewardToMessage(message, itemReward) {
  if (!itemReward) {
    return message ?? null;
  }

  validateItemReward(itemReward);

  const safeMessage = typeof message === "string" ? message : "";
  if (hasItemRewardTag(safeMessage)) {
    return safeMessage;
  }

  const encodedPayload = encodeURIComponent(
    JSON.stringify({
      itemId: itemReward.itemId.trim(),
      quantity: itemReward.quantity,
    })
  );

  const suffix = `[item_reward:${encodedPayload}]`;
  return safeMessage.length > 0 ? `${safeMessage} ${suffix}` : suffix;
}

function buildMailTypeInfo(row) {
  const title = typeof row.title === "string" ? row.title : "";
  const rewardAmount = normalizeRewardAmount(row.reward_amount);
  const hasItemReward = extractItemRewardFromMessage(row.message) !== null;

  if (title === "Project Completion") {
    return "project_completion";
  }
  if (title === "Project Reward") {
    return "project_reward";
  }
  if (hasItemReward || rewardAmount > 0) {
    return "reward";
  }
  if (rewardAmount === 0) {
    return "notification";
  }

  return "unknown";
}

function buildMailRewardInfo(row) {
  const rewardAmount = Number.isFinite(normalizeRewardAmount(row.reward_amount))
    ? normalizeRewardAmount(row.reward_amount)
    : null;
  const itemReward = extractItemRewardFromMessage(row.message);

  let rewardType = "unknown";
  if (itemReward) {
    rewardType = "item";
  } else if (rewardAmount === null) {
    rewardType = "none";
  } else if (rewardAmount > 0) {
    rewardType = "gold";
  } else if (rewardAmount <= 0) {
    rewardType = "none";
  }

  return {
    hasReward: rewardType === "gold" || rewardType === "item",
    rewardInfo: {
      rewardAmount,
      rewardType,
      itemReward,
    },
  };
}

function buildMailResult(row) {
  assertMailRow(row);
  const mailType = buildMailTypeInfo(row);
  const rewardPayload = buildMailRewardInfo(row);

  return {
    id: row.id,
    playerId: row.player_id,
    rewardAmount: normalizeRewardAmount(row.reward_amount),
    mailType,
    hasReward: rewardPayload.hasReward,
    rewardInfo: rewardPayload.rewardInfo,
  };
}

function buildMailListItem(row) {
  assertMailRow(row);
  const mailType = buildMailTypeInfo(row);
  const rewardPayload = buildMailRewardInfo(row);

  return {
    id: row.id,
    playerId: row.player_id,
    title: row.title,
    message: row.message,
    rewardAmount: normalizeRewardAmount(row.reward_amount),
    isClaimed: row.is_claimed,
    createdAt: row.created_at,
    claimedAt: row.claimed_at,
    mailType,
    hasReward: rewardPayload.hasReward,
    rewardInfo: rewardPayload.rewardInfo,
  };
}

function buildMailDetailItem(row) {
  return buildMailListItem(row);
}

function buildProjectRewardMailItem(row) {
  const rewardAmount = Number(row?.reward_amount);

  return {
    id: row?.id ?? null,
    projectId: row?.project_id ?? null,
    playerId: row?.player_id ?? null,
    contributionAmount: Number(row?.contribution_amount ?? 0),
    rewardAmount,
    createdAt: row?.created_at ?? null,
    mailType: "project_reward",
    hasReward: Number.isFinite(rewardAmount) && rewardAmount > 0,
    rewardInfo: {
      rewardAmount: Number.isFinite(rewardAmount) ? rewardAmount : null,
      rewardType: "gold",
      itemReward: null,
    },
  };
}

function buildClaimResult({ mailRow, walletRow }) {
  assertMailRow(mailRow);
  const mailType = buildMailTypeInfo(mailRow);
  const rewardPayload = buildMailRewardInfo(mailRow);

  return {
    mailId: mailRow.id,
    claimed: true,
    rewardAmount: normalizeRewardAmount(mailRow.reward_amount),
    balanceAfter: walletRow ? Number(walletRow.balance) : 0,
    mailType,
    hasReward: rewardPayload.hasReward,
    rewardInfo: rewardPayload.rewardInfo,
    mail: buildMailDetailItem(mailRow),
  };
}

async function ensurePlayerAndWallet(playerId, executor) {
  await ensurePlayerExists(playerId, executor);
  await ensureWalletExists(playerId, executor);
}

async function getMailOrThrow(mailId, executor) {
  const mail = await getMailById(mailId, executor);
  if (!mail) {
    throw new MailServiceError(mailErrorCode.MAIL_NOT_FOUND, "mail not found");
  }
  assertMailRow(mail);
  return mail;
}

async function resolveClaimFailure(mailId, executor) {
  const existingMail = await getMailById(mailId, executor);
  if (!existingMail) {
    throw new MailServiceError(mailErrorCode.MAIL_NOT_FOUND, "mail not found");
  }
  throw new MailServiceError(mailErrorCode.MAIL_ALREADY_CLAIMED, "mail already claimed");
}

async function applyMailReward({ mailRow, executor }) {
  const rewardAmount = normalizeRewardAmount(mailRow.reward_amount);

  if (rewardAmount > 0) {
    const walletAfter = await addBalance(mailRow.player_id, rewardAmount, executor);

    const ledgerEntry = buildMailLedgerEntry({
      playerId: mailRow.player_id,
      type: "add",
      amount: rewardAmount,
      reason: "mail_reward",
    });
    await insertLedgerEntry(ledgerEntry, executor);

    return walletAfter;
  }

  return findWalletByPlayerId(mailRow.player_id, executor);
}

export async function sendMail({ playerId, title, message, rewardAmount, itemReward = null }, executor) {
  validateSendMailPayload({ playerId, title, rewardAmount, itemReward });

  await ensurePlayerExists(playerId, executor);
  const finalMessage = encodeItemRewardToMessage(message, itemReward);

  const row = await createMail(
    {
      playerId,
      title: title.trim(),
      message: finalMessage,
      rewardAmount,
    },
    executor
  );

  return buildMailResult(row);
}

export async function createNotificationMail({ playerId, title, message }, executor) {
  return sendMail(
    {
      playerId,
      title,
      message,
      rewardAmount: 0,
    },
    executor
  );
}

export async function createRewardMail(
  { playerId, title, message, rewardAmount, itemReward = null },
  executor
) {
  return sendMail(
    {
      playerId,
      title,
      message,
      rewardAmount,
      itemReward,
    },
    executor
  );
}

export async function getMailbox(playerId) {
  validatePlayerId(playerId);
  const rows = await getPlayerMails(playerId);
  return rows.map(buildMailListItem);
}

export async function claimMail(mailId) {
  validateMailId(mailId);

  return withTransaction(async (client) => {
    const claimedMail = await markMailClaimed(mailId, client);
    if (!claimedMail) {
      await resolveClaimFailure(mailId, client);
    }

    const mailRow = claimedMail ?? (await getMailOrThrow(mailId, client));
    await ensurePlayerAndWallet(mailRow.player_id, client);

    const walletAfter = await applyMailReward({
      mailRow,
      executor: client,
    });

    return buildClaimResult({
      mailRow,
      walletRow: walletAfter,
    });
  });
}

function buildProjectCompletionMailPayload({ projectId, effectTarget }) {
  const targetLabel = typeof effectTarget === "string" && effectTarget.trim().length > 0
    ? effectTarget
    : "unknown";

  return {
    title: "Project Completion",
    message: `Project ${projectId} has been completed. Active effect target: ${targetLabel}. [project:${projectId}]`,
    rewardAmount: 0,
  };
}

function buildProjectRewardMailPayload({ projectName, rewardAmount }) {
  return {
    title: "Project Reward",
    message: `Thank you for supporting project ${projectName}. You received a reward based on your contribution.`,
    rewardAmount,
  };
}

export async function createProjectCompletionMail({
  playerId,
  projectId,
  effectTarget,
  executor,
}) {
  validatePlayerId(playerId);
  validateProjectId(projectId);

  const existingMail = await findProjectCompletionMail(playerId, projectId, executor);
  if (existingMail) {
    return {
      created: false,
      mail: buildMailResult(existingMail),
    };
  }

  const payload = buildProjectCompletionMailPayload({ projectId, effectTarget });
  const createdMail = await createNotificationMail(
    {
      playerId,
      title: payload.title,
      message: payload.message,
    },
    executor
  );

  return {
    created: true,
    mail: createdMail,
  };
}

export async function createProjectRewardMail(
  { playerId, projectName, rewardAmount, itemReward = null },
  executor
) {
  validatePlayerId(playerId);
  validateRewardAmount(rewardAmount);
  validateItemReward(itemReward);

  const payload = buildProjectRewardMailPayload({ projectName, rewardAmount });
  return createRewardMail(
    {
      playerId,
      title: payload.title,
      message: payload.message,
      rewardAmount: payload.rewardAmount,
      itemReward,
    },
    executor
  );
}

export async function hasProjectCompletionMail(projectId, executor) {
  validateProjectId(projectId);
  const rows = await findProjectCompletionMails(projectId, executor);
  return rows.length > 0;
}

export async function getProjectCompletionMails(projectId, executor) {
  validateProjectId(projectId);
  const rows = await findProjectCompletionMails(projectId, executor);
  return rows.map(buildMailListItem);
}

export async function getProjectRewardMails(projectId, executor) {
  validateProjectId(projectId);
  const rows = await findProjectRewardMails(projectId, executor);
  return rows.map(buildProjectRewardMailItem);
}

export async function getProjectRewardMailStats(projectId, executor) {
  validateProjectId(projectId);

  const [rewardMailCount, rewardTotalAmount, completionMails] = await Promise.all([
    countProjectRewardMails(projectId, executor),
    sumProjectRewardAmounts(projectId, executor),
    findProjectCompletionMails(projectId, executor),
  ]);

  return {
    rewardMailCount,
    rewardTotalAmount,
    completionMailSent: completionMails.length > 0,
  };
}

export async function getMailStats(executor) {
  const [totalCount, unclaimedRewardCount, projectCompletionMailCount, projectRewardMailCount] =
    await Promise.all([
      countAllMails(executor),
      countUnclaimedRewardMails(executor),
      countAllProjectCompletionMails(executor),
      countAllProjectRewardMails(executor),
    ]);

  return {
    totalCount,
    unclaimedRewardCount,
    projectCompletionMailCount,
    projectRewardMailCount,
  };
}

export async function getPlayerProjectCompletionMails(playerId, executor) {
  validatePlayerId(playerId);
  const rows = await findPlayerProjectCompletionMails(playerId, executor);
  return rows.map(buildMailListItem);
}

export async function getPlayerProjectRewardMails(playerId, executor) {
  validatePlayerId(playerId);
  const rows = await findPlayerProjectRewardMails(playerId, executor);
  return rows.map(buildProjectRewardMailItem);
}

export async function getPlayerProjectRewardMailStats(playerId, executor) {
  validatePlayerId(playerId);
  const [rewardMailCount, rewardTotalAmount] = await Promise.all([
    countPlayerProjectRewardMails(playerId, executor),
    sumPlayerProjectRewardAmounts(playerId, executor),
  ]);

  return {
    rewardMailCount,
    rewardTotalAmount,
  };
}
