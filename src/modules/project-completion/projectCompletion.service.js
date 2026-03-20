import { withTransaction } from "../../db/pool.js";
import * as mailService from "../mail/mail.service.js";
import {
  activateProjectEffectByProjectId as activateProjectEffectByProjectIdRow,
  countProjectCompletionMails,
  countProjectRewardMails,
  createProjectRewardLog,
  createProjectEffect,
  findCompletedProjectStatesWithStats,
  findProjectEffectByProjectId,
  findProjectById,
  getActiveProjectEffects,
  getProjectContributionTotals,
  getProjectByIdForUpdate,
  markProjectCompleted,
  sumProjectRewardAmounts,
} from "./projectCompletion.repository.js";

class ProjectCompletionServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "ProjectCompletionServiceError";
    this.code = code;
  }
}

export const projectCompletionErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  PROJECT_NOT_FOUND: "PROJECT_NOT_FOUND",
  PROJECT_NOT_ACTIVE: "PROJECT_NOT_ACTIVE",
  TARGET_NOT_REACHED: "TARGET_NOT_REACHED",
  EFFECT_ALREADY_EXISTS: "EFFECT_ALREADY_EXISTS",
};

const PROJECT_REGIONS = new Set([
  "agri",
  "port",
  "industry",
  "global",
  "farming",
  "fishing",
  "mining",
]);
const PROJECT_EFFECT_TARGETS = new Set(["farming", "fishing", "mining", "global"]);
const PROJECT_EFFECT_TYPES = new Set(["price_bonus", "xp_bonus", "focus_bonus"]);
const PROJECT_REGION_TO_EFFECT_TARGET = Object.freeze({
  agri: "farming",
  port: "fishing",
  industry: "mining",
  global: "global",
  farming: "farming",
  fishing: "fishing",
  mining: "mining",
});

const FIXED_EFFECT_TYPE = "price_bonus";
const FIXED_EFFECT_VALUE = 0.1;
const PROJECT_REWARD_RATE = 0.2;

export function isProjectCompletionServiceError(error) {
  return error instanceof ProjectCompletionServiceError;
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.INVALID_INPUT,
      `${fieldName} must be a valid uuid`
    );
  }
}

function validateProjectId(projectId) {
  validateUuid(projectId, "projectId");
}

function validateEffectTarget(effectTarget) {
  if (!PROJECT_EFFECT_TARGETS.has(effectTarget)) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.INVALID_INPUT,
      "effectTarget must be farming, fishing, mining, or global"
    );
  }
}

function validateEffectType(effectType) {
  if (!PROJECT_EFFECT_TYPES.has(effectType)) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.INVALID_INPUT,
      "invalid effectType"
    );
  }
}

function validateEffectValue(effectValue) {
  const numericEffectValue = Number(effectValue);
  if (!Number.isFinite(numericEffectValue) || numericEffectValue <= 0) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.INVALID_INPUT,
      "effectValue must be greater than 0"
    );
  }
}

function validateIsActive(isActive) {
  if (typeof isActive !== "boolean") {
    throw new Error("invalid project effect row");
  }
}

function normalizeNumeric(value) {
  return Number(value);
}

function normalizeEffectValue(value) {
  return Number(value);
}

function normalizeIsActive(value) {
  return Boolean(value);
}

function assertProjectForCompletionRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasName = isObject && typeof row.name === "string" && row.name.trim().length > 0;
  const hasRegion = isObject && typeof row.region === "string" && PROJECT_REGIONS.has(row.region);
  const hasStatus = isObject && typeof row.status === "string" && row.status.trim().length > 0;
  const hasCurrentAmount = isObject && Number.isFinite(normalizeNumeric(row.current_amount));
  const hasTargetAmount = isObject && Number.isFinite(normalizeNumeric(row.target_amount));

  if (
    !isObject ||
    !hasId ||
    !hasName ||
    !hasRegion ||
    !hasStatus ||
    !hasCurrentAmount ||
    !hasTargetAmount
  ) {
    throw new Error("invalid project row");
  }
}

function assertCompletedProjectRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasRegion = isObject && typeof row.region === "string" && PROJECT_REGIONS.has(row.region);
  const hasStatus = isObject && typeof row.status === "string" && row.status.trim().length > 0;
  const hasCurrentAmount = isObject && Number.isFinite(normalizeNumeric(row.current_amount));
  const hasTargetAmount = isObject && Number.isFinite(normalizeNumeric(row.target_amount));

  if (!isObject || !hasId || !hasRegion || !hasStatus || !hasCurrentAmount || !hasTargetAmount) {
    throw new Error("invalid project completion row");
  }
}

function assertCompletedProjectStateRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasProjectId =
    isObject && typeof row.project_id === "string" && row.project_id.trim().length > 0;
  const hasStatus = isObject && typeof row.status === "string" && row.status.trim().length > 0;

  if (!isObject || !hasProjectId || !hasStatus) {
    throw new Error("invalid completed project state row");
  }
}

function assertProjectEffectRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasProjectId =
    isObject && typeof row.project_id === "string" && row.project_id.trim().length > 0;
  const hasEffectType =
    isObject && typeof row.effect_type === "string" && PROJECT_EFFECT_TYPES.has(row.effect_type);
  const hasEffectTarget =
    isObject &&
    typeof row.effect_target === "string" &&
    PROJECT_EFFECT_TARGETS.has(row.effect_target);
  const hasEffectValue = isObject && Number.isFinite(normalizeEffectValue(row.effect_value));

  if (!isObject || !hasId || !hasProjectId || !hasEffectType || !hasEffectTarget || !hasEffectValue) {
    throw new Error("invalid project effect row");
  }

  validateIsActive(row.is_active);
}

function assertContributionTotalRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasPlayerId =
    isObject && typeof row.player_id === "string" && row.player_id.trim().length > 0;
  const hasContributionAmount =
    isObject && Number.isFinite(normalizeNumeric(row.contribution_amount));

  if (!isObject || !hasPlayerId || !hasContributionAmount) {
    throw new Error("invalid project contribution row");
  }
}

function assertProjectIsActive(projectRow) {
  if (projectRow.status !== "active") {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_ACTIVE,
      "project is not active"
    );
  }
}

function assertTargetReached(projectRow) {
  if (normalizeNumeric(projectRow.current_amount) < normalizeNumeric(projectRow.target_amount)) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.TARGET_NOT_REACHED,
      "target not reached"
    );
  }
}

function buildProjectEffectCreatePayload({ projectId, effectTarget }) {
  validateProjectId(projectId);
  validateEffectType(FIXED_EFFECT_TYPE);
  validateEffectTarget(effectTarget);
  validateEffectValue(FIXED_EFFECT_VALUE);

  return {
    projectId,
    effectType: FIXED_EFFECT_TYPE,
    effectTarget,
    effectValue: FIXED_EFFECT_VALUE,
  };
}

function mapProjectRegionToEffectTarget(projectRegion) {
  const mappedTarget = PROJECT_REGION_TO_EFFECT_TARGET[projectRegion];
  if (!mappedTarget) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.INVALID_INPUT,
      "invalid project region"
    );
  }
  return mappedTarget;
}

function isUniqueConstraintViolation(error) {
  return error && typeof error === "object" && error.code === "23505";
}

function buildActiveProjectEffectItem(row) {
  assertProjectEffectRow(row);

  return {
    id: row.id,
    project_id: row.project_id,
    effect_type: row.effect_type,
    effect_target: row.effect_target,
    effect_value: normalizeEffectValue(row.effect_value),
    is_active: normalizeIsActive(row.is_active),
  };
}

function buildProjectCompletionListItem(row) {
  const effect = buildActiveProjectEffectItem(row);

  return {
    projectId: effect.project_id,
    effectType: effect.effect_type,
    effectTarget: effect.effect_target,
    effectValue: effect.effect_value,
    isActive: effect.is_active,
  };
}

function buildProjectCompletionResult({ completedProject, effectRow, rewardSummary }) {
  const effect = buildActiveProjectEffectItem(effectRow);

  return {
    projectId: completedProject.id,
    status: completedProject.status,
    effect: {
      effectId: effect.id,
      effectType: effect.effect_type,
      effectTarget: effect.effect_target,
      effectValue: effect.effect_value,
      isActive: effect.is_active,
    },
    rewardSummary,
  };
}

function buildRewardSummary(totalContributors) {
  return {
    contributors: totalContributors,
    rewardMailsCreated: 0,
    totalRewardAmount: 0,
  };
}

function calculateProjectRewardAmount(contributionAmount) {
  return Math.floor(contributionAmount * PROJECT_REWARD_RATE);
}

async function createProjectRewardMail({ playerId, projectName, rewardAmount, executor }) {
  return mailService.createProjectRewardMail(
    {
      playerId,
      projectName,
      rewardAmount,
    },
    executor
  );
}

async function getProjectForCompletionOrThrow(projectId, executor) {
  const project = await getProjectByIdForUpdate(projectId, executor);
  if (!project) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_FOUND,
      "project not found"
    );
  }

  assertProjectForCompletionRow(project);
  assertProjectIsActive(project);
  assertTargetReached(project);

  return project;
}

async function ensureProjectMarkedCompleted(projectId, executor) {
  const currentProject = await findProjectById(projectId, executor);
  if (!currentProject) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_FOUND,
      "project not found"
    );
  }
  assertProjectForCompletionRow(currentProject);

  if (currentProject.status === "completed") {
    return { wasAlreadyCompleted: true };
  }

  const completed = await markProjectCompleted(projectId, executor);
  if (completed) {
    assertCompletedProjectRow(completed);
    return { wasAlreadyCompleted: false };
  }

  const latestProject = await findProjectById(projectId, executor);
  if (!latestProject) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_FOUND,
      "project not found"
    );
  }
  assertProjectForCompletionRow(latestProject);

  return { wasAlreadyCompleted: latestProject.status === "completed" };
}

async function ensureNoExistingProjectEffect(projectId, executor) {
  const existingEffect = await getProjectEffectByProjectIdOrNull(projectId, executor);
  if (!existingEffect) {
    return;
  }

  assertProjectEffectRow(existingEffect);

  throw new ProjectCompletionServiceError(
    projectCompletionErrorCode.EFFECT_ALREADY_EXISTS,
    "effect already exists"
  );
}

async function createEffectForCompletedProject({ projectId, effectTarget }, executor) {
  const payload = buildProjectEffectCreatePayload({ projectId, effectTarget });
  const effectRow = await createProjectEffect(payload, executor);
  assertProjectEffectRow(effectRow);
  return effectRow;
}

async function getProjectEffectByProjectIdOrNull(projectId, executor) {
  const row = await findProjectEffectByProjectId(projectId, executor);
  if (!row) {
    return null;
  }
  return buildActiveProjectEffectItem(row);
}

function isProjectEffectActive(row) {
  if (!row) {
    return false;
  }
  return normalizeIsActive(row.is_active);
}

function buildProjectCompletionActivationResult({
  effectRow,
  wasAlreadyActive,
  wasCreated,
  wasAlreadyCompleted = false,
}) {
  const effect = buildActiveProjectEffectItem(effectRow);

  return {
    projectId: effect.project_id,
    effect,
    wasAlreadyActive: Boolean(wasAlreadyActive),
    wasCreated: Boolean(wasCreated),
    wasAlreadyCompleted: Boolean(wasAlreadyCompleted),
    isNowActive: isProjectEffectActive(effect),
    effectTarget: effect.effect_target,
    effectType: effect.effect_type,
  };
}

function buildProjectCompletionStatusResult({ projectId, projectRow, effectRow }) {
  const hasEffectRow = Boolean(effectRow);
  const normalizedEffect = effectRow ? buildActiveProjectEffectItem(effectRow) : null;
  const projectStatus = projectRow?.status;
  const isCompleted = projectStatus === "completed";
  const isEffectActive = normalizedEffect ? isProjectEffectActive(normalizedEffect) : false;

  return {
    projectId,
    hasEffectRow,
    isCompleted,
    isEffectActive,
    effectTarget: normalizedEffect ? normalizedEffect.effect_target : null,
    effectType: normalizedEffect ? normalizedEffect.effect_type : null,
    rewardMailCount: 0,
    rewardTotalAmount: 0,
    completionMailSent: false,
    completionProcessed: isCompleted && hasEffectRow && isEffectActive,
  };
}

function buildProjectRewardSummaryResult({
  rewardMailCount,
  rewardTotalAmount,
  completionMailSent,
}) {
  const safeRewardMailCount = Number(rewardMailCount) || 0;
  const safeRewardTotalAmount = Number(rewardTotalAmount) || 0;

  return {
    rewardMailCount: safeRewardMailCount,
    rewardTotalAmount: safeRewardTotalAmount,
    completionMailSent: Boolean(completionMailSent),
  };
}

function buildCompletedProjectListItem({ row, rewardStats }) {
  assertCompletedProjectStateRow(row);
  const effectRow =
    row.effect_id !== null
      ? {
          id: row.effect_id,
          project_id: row.project_id,
          effect_type: row.effect_type,
          effect_target: row.effect_target,
          effect_value: row.effect_value,
          is_active: row.is_active,
        }
      : null;

  const normalizedEffect = effectRow ? buildActiveProjectEffectItem(effectRow) : null;
  const isEffectActive = normalizedEffect ? isProjectEffectActive(normalizedEffect) : false;
  const hasEffectRow = normalizedEffect !== null;
  const isCompleted = row.status === "completed";

  return {
    projectId: row.project_id,
    isCompleted,
    isEffectActive,
    effectTarget: normalizedEffect ? normalizedEffect.effect_target : null,
    effectType: normalizedEffect ? normalizedEffect.effect_type : null,
    rewardMailCount: Number(rewardStats?.rewardMailCount ?? 0),
    rewardTotalAmount: Number(rewardStats?.rewardTotalAmount ?? 0),
    completionMailSent: Boolean(rewardStats?.completionMailSent),
    completionProcessed: isCompleted && hasEffectRow && isEffectActive,
  };
}

async function getProjectRewardMailStats(projectId, executor) {
  const [rewardMailCount, rewardTotalAmount, completionMailCount] = await Promise.all([
    countProjectRewardMails(projectId, executor),
    sumProjectRewardAmounts(projectId, executor),
    countProjectCompletionMails(projectId, executor),
  ]);

  return buildProjectRewardSummaryResult({
    rewardMailCount,
    rewardTotalAmount,
    completionMailSent: Number(completionMailCount) > 0,
  });
}

async function getValidatedContributionTotals(projectId, executor) {
  const rows = await getProjectContributionTotals(projectId, executor);
  for (const row of rows) {
    assertContributionTotalRow(row);
  }
  return rows;
}

async function distributeProjectRewards({ projectId, projectName, contributionTotals, executor }) {
  const rewardSummary = buildRewardSummary(contributionTotals.length);

  for (const contribution of contributionTotals) {
    const contributionAmount = normalizeNumeric(contribution.contribution_amount);
    const rewardAmount = calculateProjectRewardAmount(contributionAmount);

    if (rewardAmount <= 0) {
      continue;
    }

    const rewardLog = await createProjectRewardLog(
      {
        projectId,
        playerId: contribution.player_id,
        contributionAmount,
        rewardAmount,
      },
      executor
    );

    if (!rewardLog) {
      continue;
    }

    await createProjectRewardMail({
      playerId: contribution.player_id,
      projectName,
      rewardAmount,
      executor,
    });

    rewardSummary.rewardMailsCreated += 1;
    rewardSummary.totalRewardAmount += rewardAmount;
  }

  return rewardSummary;
}

export async function activateProjectEffectByProjectId(projectId, executor) {
  validateProjectId(projectId);
  const completionStatus = await ensureProjectMarkedCompleted(projectId, executor);
  const wasAlreadyCompleted = completionStatus.wasAlreadyCompleted === true;

  const existingEffect = await getProjectEffectByProjectIdOrNull(projectId, executor);
  if (existingEffect) {
    if (isProjectEffectActive(existingEffect)) {
      return buildProjectCompletionActivationResult({
        effectRow: existingEffect,
        wasAlreadyActive: true,
        wasCreated: false,
        wasAlreadyCompleted,
      });
    }

    const activatedEffect = await activateProjectEffectByProjectIdRow(projectId, executor);
    if (!activatedEffect) {
      const latestEffect = await getProjectEffectByProjectIdOrNull(projectId, executor);
      if (!latestEffect) {
        throw new Error("failed to activate project effect");
      }
      return buildProjectCompletionActivationResult({
        effectRow: latestEffect,
        wasAlreadyActive: false,
        wasCreated: false,
        wasAlreadyCompleted,
      });
    }

    return buildProjectCompletionActivationResult({
      effectRow: activatedEffect,
      wasAlreadyActive: false,
      wasCreated: false,
      wasAlreadyCompleted,
    });
  }

  const project = await getProjectByIdForUpdate(projectId, executor);
  if (!project) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_FOUND,
      "project not found"
    );
  }
  assertProjectForCompletionRow(project);

  const effectTarget = mapProjectRegionToEffectTarget(project.region);

  try {
    const createdEffect = await createEffectForCompletedProject(
      { projectId, effectTarget },
      executor
    );
    return buildProjectCompletionActivationResult({
      effectRow: createdEffect,
      wasAlreadyActive: false,
      wasCreated: true,
      wasAlreadyCompleted,
    });
  } catch (error) {
    if (!isUniqueConstraintViolation(error)) {
      throw error;
    }
  }

  const conflictedEffect = await getProjectEffectByProjectIdOrNull(projectId, executor);
  if (!conflictedEffect) {
    throw new Error("failed to create or fetch project effect");
  }

  if (isProjectEffectActive(conflictedEffect)) {
    return buildProjectCompletionActivationResult({
      effectRow: conflictedEffect,
      wasAlreadyActive: true,
      wasCreated: false,
      wasAlreadyCompleted,
    });
  }

  const activatedAfterConflict = await activateProjectEffectByProjectIdRow(projectId, executor);
  if (!activatedAfterConflict) {
    const latestEffect = await getProjectEffectByProjectIdOrNull(projectId, executor);
    if (!latestEffect) {
      throw new Error("failed to activate project effect");
    }
    return buildProjectCompletionActivationResult({
      effectRow: latestEffect,
      wasAlreadyActive: false,
      wasCreated: false,
      wasAlreadyCompleted,
    });
  }

  return buildProjectCompletionActivationResult({
    effectRow: activatedAfterConflict,
    wasAlreadyActive: false,
    wasCreated: false,
    wasAlreadyCompleted,
  });
}

export async function getProjectCompletionStatus(projectId, executor) {
  validateProjectId(projectId);

  const project = await findProjectById(projectId, executor);
  if (!project) {
    throw new ProjectCompletionServiceError(
      projectCompletionErrorCode.PROJECT_NOT_FOUND,
      "project not found"
    );
  }
  assertProjectForCompletionRow(project);

  const effect = await getProjectEffectByProjectIdOrNull(projectId, executor);
  const baseStatus = buildProjectCompletionStatusResult({
    projectId,
    projectRow: project,
    effectRow: effect,
  });
  const rewardStats = await getProjectRewardMailStats(projectId, executor);

  return {
    ...baseStatus,
    ...rewardStats,
    completionProcessed: baseStatus.completionProcessed,
  };
}

export async function getCompletedProjects(executor) {
  const rows = await findCompletedProjectStatesWithStats(executor);
  const items = rows.map((row) =>
    buildCompletedProjectListItem({
      row,
      rewardStats: {
        rewardMailCount: Number(row.reward_mail_count ?? 0),
        rewardTotalAmount: Number(row.reward_total_amount ?? 0),
        completionMailSent: Number(row.completion_mail_count ?? 0) > 0,
      },
    })
  );

  return { items };
}

export async function completeProject(projectId) {
  validateProjectId(projectId);

  return withTransaction(async (client) => {
    const project = await getProjectForCompletionOrThrow(projectId, client);

    await ensureNoExistingProjectEffect(projectId, client);

    const completed = await markProjectCompleted(projectId, client);
    if (!completed) {
      throw new ProjectCompletionServiceError(
        projectCompletionErrorCode.PROJECT_NOT_ACTIVE,
        "project is not active"
      );
    }
    assertCompletedProjectRow(completed);

    const effect = await createEffectForCompletedProject(
      {
        projectId,
        effectTarget: mapProjectRegionToEffectTarget(completed.region),
      },
      client
    );

    const contributionTotals = await getValidatedContributionTotals(projectId, client);
    const rewardSummary = await distributeProjectRewards({
      projectId,
      projectName: project.name,
      contributionTotals,
      executor: client,
    });

    return buildProjectCompletionResult({
      completedProject: completed,
      effectRow: effect,
      rewardSummary,
    });
  });
}

export async function getActiveEffects() {
  const rows = await getActiveProjectEffects();
  return rows.map(buildActiveProjectEffectItem);
}


