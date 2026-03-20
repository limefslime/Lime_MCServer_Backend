import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function createMail({ playerId, title, message, rewardAmount }, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO player_mail (player_id, title, message, reward_amount)
    VALUES ($1, $2, $3, $4)
    RETURNING
      id,
      player_id,
      title,
      message,
      reward_amount,
      is_claimed,
      created_at,
      claimed_at
    `,
    [playerId, title, message ?? null, rewardAmount]
  );
  return result.rows[0];
}

export async function getPlayerMails(playerId, executor) {
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
    WHERE player_id = $1
    ORDER BY created_at DESC
    `,
    [playerId]
  );
  return result.rows;
}

export async function countAllMails(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS total_count
    FROM player_mail
    `
  );
  return Number(result.rows[0]?.total_count ?? 0);
}

export async function countUnclaimedRewardMails(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS unclaimed_reward_count
    FROM player_mail
    WHERE is_claimed = FALSE
      AND (
        reward_amount > 0
        OR message LIKE '%[item_reward:%'
      )
    `
  );
  return Number(result.rows[0]?.unclaimed_reward_count ?? 0);
}

export async function countAllProjectCompletionMails(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS completion_mail_count
    FROM player_mail
    WHERE title = 'Project Completion'
    `
  );
  return Number(result.rows[0]?.completion_mail_count ?? 0);
}

export async function countAllProjectRewardMails(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS project_reward_mail_count
    FROM project_reward_logs
    `
  );
  return Number(result.rows[0]?.project_reward_mail_count ?? 0);
}

export async function getMailById(mailId, executor, options = {}) {
  const db = getExecutor(executor);
  const lockClause = options.forUpdate ? "FOR UPDATE" : "";
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
    WHERE id = $1
    ${lockClause}
    `,
    [mailId]
  );
  return result.rows[0] ?? null;
}

export async function findProjectCompletionMail(playerId, projectId, executor) {
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
    WHERE player_id = $1
      AND title = 'Project Completion'
      AND message LIKE $2
    ORDER BY created_at DESC
    LIMIT 1
    `,
    [playerId, `%[project:${projectId}]%`]
  );
  return result.rows[0] ?? null;
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

export async function findPlayerProjectCompletionMails(playerId, executor) {
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
    WHERE player_id = $1
      AND title = 'Project Completion'
    ORDER BY created_at DESC
    `,
    [playerId]
  );
  return result.rows;
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

export async function findProjectRewardMails(projectId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      project_id,
      player_id,
      contribution_amount,
      reward_amount,
      created_at
    FROM project_reward_logs
    WHERE project_id = $1
    ORDER BY created_at DESC
    `,
    [projectId]
  );
  return result.rows;
}

export async function findPlayerProjectRewardMails(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      project_id,
      player_id,
      contribution_amount,
      reward_amount,
      created_at
    FROM project_reward_logs
    WHERE player_id = $1
    ORDER BY created_at DESC
    `,
    [playerId]
  );
  return result.rows;
}

export async function countPlayerProjectRewardMails(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COUNT(*)::BIGINT AS reward_mail_count
    FROM project_reward_logs
    WHERE player_id = $1
    `,
    [playerId]
  );
  return Number(result.rows[0]?.reward_mail_count ?? 0);
}

export async function sumPlayerProjectRewardAmounts(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT COALESCE(SUM(reward_amount), 0)::BIGINT AS reward_total_amount
    FROM project_reward_logs
    WHERE player_id = $1
    `,
    [playerId]
  );
  return Number(result.rows[0]?.reward_total_amount ?? 0);
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

export async function markMailClaimed(mailId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE player_mail
    SET
      is_claimed = TRUE,
      claimed_at = NOW()
    WHERE id = $1
      AND is_claimed = FALSE
    RETURNING
      id,
      player_id,
      title,
      message,
      reward_amount,
      is_claimed,
      created_at,
      claimed_at
    `,
    [mailId]
  );
  return result.rows[0] ?? null;
}
