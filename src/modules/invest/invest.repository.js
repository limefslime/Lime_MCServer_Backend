import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function createProject({ name, description, targetAmount, region, endsAt }, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO invest_projects (
      name,
      description,
      target_amount,
      region,
      ends_at
    )
    VALUES ($1, $2, $3, $4, $5)
    RETURNING
      id,
      name,
      description,
      target_amount,
      current_amount,
      region,
      status,
      created_at,
      ends_at
    `,
    [name, description ?? null, targetAmount, region, endsAt ?? null]
  );
  return result.rows[0];
}

export async function getActiveProjects(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      description,
      target_amount,
      current_amount,
      region,
      status,
      created_at,
      ends_at
    FROM invest_projects
    WHERE status = 'active'
    ORDER BY created_at DESC
    `
  );
  return result.rows;
}

export async function getProjectById(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      description,
      target_amount,
      current_amount,
      region,
      status,
      created_at,
      ends_at
    FROM invest_projects
    WHERE id = $1
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function addContribution({ projectId, playerId, amount }, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO invest_contributions (project_id, player_id, amount)
    VALUES ($1, $2, $3)
    RETURNING id, project_id, player_id, amount, created_at
    `,
    [projectId, playerId, amount]
  );
  return result.rows[0];
}

export async function getProjectTotal(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT current_amount
    FROM invest_projects
    WHERE id = $1
    `,
    [projectId]
  );
  if (!result.rows[0]) {
    return null;
  }
  return Number(result.rows[0].current_amount);
}

export async function increaseProjectAmount(projectId, amount, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE invest_projects
    SET
      current_amount = current_amount + $2
    WHERE id = $1
    RETURNING
      id,
      name,
      description,
      target_amount,
      current_amount,
      region,
      status,
      created_at,
      ends_at
    `,
    [projectId, amount]
  );
  return result.rows[0] ?? null;
}

export async function getProjectFundingStatus(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      target_amount,
      current_amount,
      status
    FROM invest_projects
    WHERE id = $1
    `,
    [projectId]
  );
  return result.rows[0] ?? null;
}

export async function getProjectProgressSnapshot(projectId, executor) {
  return getProjectFundingStatus(projectId, executor);
}

export async function getPlayerContributionAmount(projectId, playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COALESCE(SUM(amount), 0)::BIGINT AS contribution_amount
    FROM invest_contributions
    WHERE project_id = $1
      AND player_id = $2
    `,
    [projectId, playerId]
  );
  return Number(result.rows[0]?.contribution_amount ?? 0);
}

export async function getProjectContributions(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT player_id, amount, created_at
    FROM invest_contributions
    WHERE project_id = $1
    ORDER BY created_at DESC
    `,
    [projectId]
  );
  return result.rows;
}
