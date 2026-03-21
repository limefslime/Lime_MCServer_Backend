import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function ensureVillageFundRow(executor) {
  const db = getExecutor(executor);
  await db.query(
    `
    INSERT INTO village_fund (id, total_amount, level, next_level_requirement)
    VALUES (1, 0, 1, 10000)
    ON CONFLICT (id) DO NOTHING
    `
  );
}

export async function getVillageFund(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      total_amount,
      level,
      next_level_requirement,
      updated_at
    FROM village_fund
    WHERE id = 1
    `
  );
  return result.rows[0] ?? null;
}

export async function getVillageFundForUpdate(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      total_amount,
      level,
      next_level_requirement,
      updated_at
    FROM village_fund
    WHERE id = 1
    FOR UPDATE
    `
  );
  return result.rows[0] ?? null;
}

export async function updateVillageFund(
  { totalAmount, level, nextLevelRequirement },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE village_fund
    SET
      total_amount = $1,
      level = $2,
      next_level_requirement = $3,
      updated_at = NOW()
    WHERE id = 1
    RETURNING
      id,
      total_amount,
      level,
      next_level_requirement,
      updated_at
    `,
    [totalAmount, level, nextLevelRequirement]
  );
  return result.rows[0] ?? null;
}

export async function getPlayerContribution(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      player_id,
      total_contribution,
      updated_at
    FROM player_contribution
    WHERE player_id = $1
    `,
    [playerId]
  );
  return result.rows[0] ?? null;
}

export async function addPlayerContribution(playerId, amount, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO player_contribution (player_id, total_contribution, updated_at)
    VALUES ($1, $2, NOW())
    ON CONFLICT (player_id)
    DO UPDATE SET
      total_contribution = player_contribution.total_contribution + EXCLUDED.total_contribution,
      updated_at = NOW()
    RETURNING
      player_id,
      total_contribution,
      updated_at
    `,
    [playerId, amount]
  );
  return result.rows[0] ?? null;
}
