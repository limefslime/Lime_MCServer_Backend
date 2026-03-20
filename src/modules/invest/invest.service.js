import { withTransaction } from "../../db/pool.js";
import { createProjectCompletionMail } from "../mail/mail.service.js";
import {
  activateProjectEffectByProjectId,
  getProjectCompletionStatus,
} from "../project-completion/projectCompletion.service.js";
import {
  ensurePlayerExists,
  ensureWalletExists,
  insertLedgerEntry,
  subtractBalanceIfEnough,
} from "../wallet/wallet.repository.js";
import {
  addContribution,
  createProject as createProjectRow,
  getActiveProjects,
  getProjectById,
  getProjectContributions,
  getPlayerContributionAmount,
  getProjectProgressSnapshot as getProjectProgressSnapshotRow,
  getProjectTotal,
  increaseProjectAmount,
} from "./invest.repository.js";

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
};

const PROJECT_REGIONS = new Set(["agri", "port", "industry", "global"]);
const INVEST_LEDGER_REASON = "invest_project";

export function isInvestServiceError(error) {
  return error instanceof InvestServiceError;
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, `${fieldName} must be a valid uuid`);
  }
}

function validatePlayerId(playerId) {
  validateUuid(playerId, "playerId");
}

function validateProjectId(projectId) {
  validateUuid(projectId, "projectId");
}

function validateAmount(amount) {
  if (!Number.isInteger(amount) || amount <= 0) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, "amount must be a positive integer");
  }
}

function validateProjectInput({ name, targetAmount, region }) {
  if (typeof name !== "string" || name.trim().length === 0) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, "name is required");
  }
  if (!Number.isInteger(targetAmount) || targetAmount <= 0) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, "targetAmount must be a positive integer");
  }
  if (!PROJECT_REGIONS.has(region)) {
    throw new InvestServiceError(
      investErrorCode.INVALID_INPUT,
      "region must be agri, port, industry, or global"
    );
  }
}

function assertProjectExists(project) {
  if (!project) {
    throw new InvestServiceError(investErrorCode.PROJECT_NOT_FOUND, "project not found");
  }
}

function assertProjectRow(project) {
  const isObject = typeof project === "object" && project !== null;
  const hasId = isObject && typeof project.id === "string" && project.id.trim().length > 0;
  const hasName = isObject && typeof project.name === "string" && project.name.trim().length > 0;
  const hasStatus = isObject && typeof project.status === "string" && project.status.trim().length > 0;
  const hasValidTargetAmount = isObject && Number.isFinite(Number(project.target_amount));
  const hasValidCurrentAmount = isObject && Number.isFinite(Number(project.current_amount));

  if (!isObject || !hasId || !hasName || !hasStatus || !hasValidTargetAmount || !hasValidCurrentAmount) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, "invalid project data");
  }
}

function assertContributionRow(contribution) {
  const isObject = typeof contribution === "object" && contribution !== null;
  const hasPlayerId =
    isObject &&
    typeof contribution.player_id === "string" &&
    contribution.player_id.trim().length > 0;
  const hasAmount = isObject && Number.isFinite(Number(contribution.amount));

  if (!isObject || !hasPlayerId || !hasAmount) {
    throw new InvestServiceError(investErrorCode.INVALID_INPUT, "invalid contribution data");
  }
}

function assertProjectStillInvestable(project) {
  if (project.status !== "active") {
    throw new InvestServiceError(investErrorCode.PROJECT_NOT_ACTIVE, "project is not active");
  }
}

function normalizeProjectAmounts(row) {
  assertProjectRow(row);

  return {
    targetAmount: Number(row.target_amount),
    currentAmount: Number(row.current_amount),
  };
}

function calculateProjectProgress(targetAmount, currentAmount) {
  return targetAmount > 0 ? currentAmount / targetAmount : 0;
}

function buildInvestListItem(row) {
  const { targetAmount, currentAmount } = normalizeProjectAmounts(row);

  return {
    id: row.id,
    name: row.name,
    targetAmount,
    currentAmount,
    progress: calculateProjectProgress(targetAmount, currentAmount),
    status: row.status,
  };
}

function buildProjectCreateResult(row) {
  const { targetAmount, currentAmount } = normalizeProjectAmounts(row);

  return {
    id: row.id,
    name: row.name,
    targetAmount,
    currentAmount,
    status: row.status,
  };
}

function buildProjectDetailResult(row, contributors) {
  const { targetAmount, currentAmount } = normalizeProjectAmounts(row);

  return {
    id: row.id,
    name: row.name,
    description: row.description,
    targetAmount,
    currentAmount,
    progress: calculateProjectProgress(targetAmount, currentAmount),
    contributors,
  };
}

function buildInvestLedgerEntry({ playerId, amount, type, reason }) {
  return {
    playerId,
    type,
    amount,
    reason,
  };
}

function buildContributionPayload({ projectId, playerId, amount }) {
  return {
    projectId,
    playerId,
    amount,
  };
}

function buildInvestResult({ projectId, amount, projectTotal }) {
  return {
    projectId,
    invested: amount,
    projectTotal,
  };
}

async function getProjectProgressSnapshot(projectId, executor) {
  return getProjectProgressSnapshotRow(projectId, executor);
}

function toFiniteNumberOrNull(value) {
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? numericValue : null;
}

function calculateProgressPercent(targetAmount, currentAmount) {
  if (!Number.isFinite(targetAmount) || targetAmount <= 0) {
    return null;
  }
  if (!Number.isFinite(currentAmount)) {
    return null;
  }
  return currentAmount / targetAmount;
}

function buildInvestmentProgressInfo({ fundingStatus, contributionAmount }) {
  const currentAmount = toFiniteNumberOrNull(fundingStatus?.current_amount);
  const targetAmount = toFiniteNumberOrNull(fundingStatus?.target_amount);

  let remainingAmount = null;
  if (targetAmount !== null && currentAmount !== null) {
    remainingAmount = Math.max(targetAmount - currentAmount, 0);
  }

  return {
    currentAmount,
    targetAmount,
    remainingAmount,
    progressPercent: calculateProgressPercent(targetAmount, currentAmount),
    contributionAmount: toFiniteNumberOrNull(contributionAmount),
  };
}

function shouldActivateProjectEffect(projectFundingStatus) {
  if (!projectFundingStatus || typeof projectFundingStatus !== "object") {
    return false;
  }

  const currentAmount = Number(projectFundingStatus.current_amount);
  const targetAmount = Number(projectFundingStatus.target_amount);

  if (!Number.isFinite(currentAmount) || !Number.isFinite(targetAmount)) {
    return false;
  }

  if (targetAmount <= 0) {
    return false;
  }

  return currentAmount >= targetAmount;
}

function buildInvestmentCompletionInfo({
  reachedTarget,
  activationResult,
  createdCompletionMail = false,
  completionStatus = null,
}) {
  if (!reachedTarget) {
    return {
      reachedTarget: false,
      activatedEffect: false,
      wasAlreadyCompleted: false,
      effectTarget: null,
      effectType: null,
      createdCompletionMail: false,
      completionProcessed: false,
      rewardMailCount: 0,
      rewardTotalAmount: 0,
    };
  }

  const wasAlreadyActive = activationResult?.wasAlreadyActive === true;
  const wasAlreadyCompletedFromProject = activationResult?.wasAlreadyCompleted === true;
  const wasAlreadyCompleted = wasAlreadyCompletedFromProject || wasAlreadyActive;
  const isNowActive = activationResult?.isNowActive === true;
  const activatedEffect =
    isNowActive && !wasAlreadyActive && !wasAlreadyCompletedFromProject;
  const effectTarget =
    activationResult?.effectTarget ?? activationResult?.effect?.effect_target ?? null;
  const effectType = activationResult?.effectType ?? activationResult?.effect?.effect_type ?? null;
  const completionProcessedFromStatus =
    completionStatus && typeof completionStatus === "object"
      ? Boolean(completionStatus.completionProcessed)
      : false;
  const completionProcessed = activatedEffect || wasAlreadyCompleted || completionProcessedFromStatus;
  const rewardMailCount =
    completionStatus && typeof completionStatus === "object"
      ? Number(completionStatus.rewardMailCount ?? 0)
      : 0;
  const rewardTotalAmount =
    completionStatus && typeof completionStatus === "object"
      ? Number(completionStatus.rewardTotalAmount ?? 0)
      : 0;

  return {
    reachedTarget: true,
    activatedEffect,
    wasAlreadyCompleted,
    effectTarget,
    effectType,
    createdCompletionMail: Boolean(createdCompletionMail),
    completionProcessed,
    rewardMailCount,
    rewardTotalAmount,
  };
}

async function evaluateProjectCompletionAfterInvestment(projectId, projectFundingStatus, executor) {
  const reachedTarget = shouldActivateProjectEffect(projectFundingStatus);
  if (!reachedTarget) {
    return {
      completionInfo: buildInvestmentCompletionInfo({ reachedTarget, activationResult: null }),
      activationResult: null,
    };
  }

  const activationResult = await activateProjectEffectByProjectId(projectId, executor);
  return {
    completionInfo: buildInvestmentCompletionInfo({ reachedTarget, activationResult }),
    activationResult,
  };
}

function shouldCreateCompletionMail(completionInfo) {
  return (
    completionInfo.reachedTarget === true &&
    completionInfo.activatedEffect === true &&
    completionInfo.wasAlreadyCompleted === false
  );
}

async function ensureInvestorWalletReady(playerId, executor) {
  await ensurePlayerExists(playerId, executor);
  await ensureWalletExists(playerId, executor);
}

async function getProjectOrThrow(projectId, executor) {
  const project = await getProjectById(projectId, executor);
  assertProjectExists(project);
  assertProjectRow(project);
  return project;
}

async function getActiveProjectOrThrow(projectId, executor) {
  const project = await getProjectOrThrow(projectId, executor);
  assertProjectStillInvestable(project);
  return project;
}

async function subtractWalletOrThrow(playerId, amount, executor) {
  const wallet = await subtractBalanceIfEnough(playerId, amount, executor);
  if (!wallet) {
    throw new InvestServiceError(investErrorCode.INSUFFICIENT_BALANCE, "insufficient balance");
  }
  return wallet;
}

function countUniqueContributors(contributions) {
  const contributorSet = new Set();

  for (const contribution of contributions) {
    assertContributionRow(contribution);
    contributorSet.add(contribution.player_id);
  }

  return contributorSet.size;
}

export async function createProject(payload) {
  validateProjectInput(payload);

  const row = await createProjectRow({
    name: payload.name.trim(),
    description: payload.description ?? null,
    targetAmount: payload.targetAmount,
    region: payload.region,
    endsAt: payload.endsAt ?? null,
  });

  return buildProjectCreateResult(row);
}

export async function getProjects() {
  const rows = await getActiveProjects();
  return rows.map(buildInvestListItem);
}

export async function getProjectDetail(projectId) {
  validateProjectId(projectId);

  const row = await getProjectOrThrow(projectId);
  const contributions = await getProjectContributions(projectId);
  const contributors = countUniqueContributors(contributions);

  return buildProjectDetailResult(row, contributors);
}

export async function investToProject(projectId, { playerId, amount }) {
  validateProjectId(projectId);
  validatePlayerId(playerId);
  validateAmount(amount);

  return withTransaction(async (client) => {
    await getActiveProjectOrThrow(projectId, client);
    await ensureInvestorWalletReady(playerId, client);
    await subtractWalletOrThrow(playerId, amount, client);

    const ledgerEntry = buildInvestLedgerEntry({
      playerId,
      type: "subtract",
      amount,
      reason: INVEST_LEDGER_REASON,
    });
    await insertLedgerEntry(ledgerEntry, client);

    const contributionPayload = buildContributionPayload({
      projectId,
      playerId,
      amount,
    });
    await addContribution(contributionPayload, client);

    const updatedProject = await increaseProjectAmount(projectId, amount, client);
    const latestProjectFundingStatus = await getProjectProgressSnapshot(projectId, client);
    const { completionInfo, activationResult } = await evaluateProjectCompletionAfterInvestment(
      projectId,
      latestProjectFundingStatus,
      client
    );
    let completion = completionInfo;
    let createdCompletionMail = false;

    if (shouldCreateCompletionMail(completion)) {
      const completionMail = await createProjectCompletionMail({
        playerId,
        projectId,
        effectTarget: completion.effectTarget,
        executor: client,
      });
      createdCompletionMail = completionMail.created;
    }

    let completionStatus = null;
    if (completion.reachedTarget) {
      completionStatus = await getProjectCompletionStatus(projectId, client);
    }

    completion = buildInvestmentCompletionInfo({
      reachedTarget: completion.reachedTarget,
      activationResult,
      createdCompletionMail,
      completionStatus,
    });

    const contributionAmount = await getPlayerContributionAmount(projectId, playerId, client);
    const progress = buildInvestmentProgressInfo({
      fundingStatus: latestProjectFundingStatus,
      contributionAmount,
    });

    const projectTotal = latestProjectFundingStatus
      ? Number(latestProjectFundingStatus.current_amount)
      : updatedProject
        ? normalizeProjectAmounts(updatedProject).currentAmount
        : await getProjectTotal(projectId, client);

    const investResult = buildInvestResult({
      projectId,
      amount,
      projectTotal,
    });
    return {
      ...investResult,
      progress,
      completion,
    };
  });
}

export async function getProjectProgress(projectId) {
  validateProjectId(projectId);

  const project = await getProjectOrThrow(projectId);
  const fundingStatus = await getProjectProgressSnapshot(projectId);
  const progress = buildInvestmentProgressInfo({
    fundingStatus,
    contributionAmount: null,
  });

  return {
    projectId: project.id,
    progress,
  };
}
