import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function getProjectByIdForUpdate(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      target_amount,
      current_amount,
      region,
      status
    FROM invest_projects
    WHERE id = $1
    FOR UPDATE
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function findProjectById(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      target_amount,
      current_amount,
      region,
      status
    FROM invest_projects
    WHERE id = $1
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function markProjectCompleted(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE invest_projects
    SET status = 'completed'
    WHERE id = $1
      AND status = 'active'
    RETURNING id, status, region, current_amount, target_amount
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function createProjectEffect(
  { projectId, effectType, effectTarget, effectValue },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO project_effects (
      project_id,
      effect_type,
      effect_target,
      effect_value
    )
    VALUES ($1, $2, $3, $4)
    RETURNING
      id,
      project_id,
      effect_type,
      effect_target,
      effect_value,
      is_active,
      created_at
    `,
    [projectId, effectType, effectTarget, effectValue]
  );
  return result.rows[0];
}

export async function getActiveProjectEffects(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      project_id,
      effect_type,
      effect_target,
      effect_value,
      is_active,
      created_at
    FROM project_effects
    WHERE is_active = TRUE
    ORDER BY created_at DESC
    `
  );
  return result.rows;
}

export async function findProjectEffectByProjectId(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      project_id,
      effect_type,
      effect_target,
      effect_value,
      is_active,
      created_at
    FROM project_effects
    WHERE project_id = $1
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function getProjectEffectByProjectId(projectId, executor) {
  return findProjectEffectByProjectId(projectId, executor);
}

export async function activateProjectEffectByProjectId(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE project_effects
    SET is_active = TRUE
    WHERE project_id = $1
      AND is_active = FALSE
    RETURNING
      id,
      project_id,
      effect_type,
      effect_target,
      effect_value,
      is_active,
      created_at
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function getProjectContributionTotals(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      player_id,
      SUM(amount)::BIGINT AS contribution_amount
    FROM invest_contributions
    WHERE project_id = $1
    GROUP BY player_id
    ORDER BY player_id
    `,
    [projectId]
  );
  return result.rows;
}

export async function createProjectRewardLog(
  { projectId, playerId, contributionAmount, rewardAmount },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO project_reward_logs (
      project_id,
      player_id,
      contribution_amount,
      reward_amount
    )
    VALUES ($1, $2, $3, $4)
    ON CONFLICT (project_id, player_id) DO NOTHING
    RETURNING
      id,
      project_id,
      player_id,
      contribution_amount,
      reward_amount,
      created_at
    `,
    [projectId, playerId, contributionAmount, rewardAmount]
  );
  return result.rows[0] ?? null;
}

export async function countProjectRewardMails(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS reward_mail_count
    FROM project_reward_logs
    WHERE project_id = $1
    `,
    [projectId]
  );
  return Number(result.rows[0]?.reward_mail_count ?? 0);
}

export async function sumProjectRewardAmounts(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COALESCE(SUM(reward_amount), 0)::BIGINT AS reward_total_amount
    FROM project_reward_logs
    WHERE project_id = $1
    `,
    [projectId]
  );
  return Number(result.rows[0]?.reward_total_amount ?? 0);
}

export async function findProjectCompletionMails(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      player_id,
      title,
      message,
      reward_amount,
      is_claimed,
      created_at,
      claimed_at
    FROM player_mail
    WHERE title = 'Project Completion'
      AND message LIKE $1
    ORDER BY created_at DESC
    `,
    [`%[project:${projectId}]%`]
  );
  return result.rows;
}

export async function countProjectCompletionMails(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS completion_mail_count
    FROM player_mail
    WHERE title = 'Project Completion'
      AND message LIKE $1
    `,
    [`%[project:${projectId}]%`]
  );
  return Number(result.rows[0]?.completion_mail_count ?? 0);
}

export async function findCompletedProjectStates(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      p.id AS project_id,
      p.status,
      pe.id AS effect_id,
      pe.effect_type,
      pe.effect_target,
      pe.effect_value,
      pe.is_active
    FROM invest_projects p
    LEFT JOIN project_effects pe
      ON pe.project_id = p.id
    WHERE p.status = 'completed'
    ORDER BY p.created_at DESC
    `
  );
  return result.rows;
}

export async function findCompletedProjectStatesWithStats(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    WITH reward_stats AS (
      SELECT
        project_id,
        COUNT(*)::BIGINT AS reward_mail_count,
        COALESCE(SUM(reward_amount), 0)::BIGINT AS reward_total_amount
      FROM project_reward_logs
      GROUP BY project_id
    ),
    completion_mail_stats AS (
      SELECT
        (regexp_match(
          message,
          '\\[project:([0-9a-fA-F-]{8}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{12})\\]'
        ))[1]::UUID AS project_id,
        COUNT(*)::BIGINT AS completion_mail_count
      FROM player_mail
      WHERE title = 'Project Completion'
        AND message ~ '\\[project:[0-9a-fA-F-]{8}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{4}-[0-9a-fA-F-]{12}\\]'
      GROUP BY 1
    )
    SELECT
      p.id AS project_id,
      p.status,
      pe.id AS effect_id,
      pe.effect_type,
      pe.effect_target,
      pe.effect_value,
      pe.is_active,
      COALESCE(rs.reward_mail_count, 0)::BIGINT AS reward_mail_count,
      COALESCE(rs.reward_total_amount, 0)::BIGINT AS reward_total_amount,
      COALESCE(cms.completion_mail_count, 0)::BIGINT AS completion_mail_count
    FROM invest_projects p
    LEFT JOIN project_effects pe
      ON pe.project_id = p.id
    LEFT JOIN reward_stats rs
      ON rs.project_id = p.id
    LEFT JOIN completion_mail_stats cms
      ON cms.project_id = p.id
    WHERE p.status = 'completed'
    ORDER BY p.created_at DESC
    `
  );
  return result.rows;
}
