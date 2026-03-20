import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function findAllRegionProgress(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT region, xp, level, updated_at
    FROM region_progress
    ORDER BY
      CASE region
        WHEN 'agri' THEN 1
        WHEN 'port' THEN 2
        WHEN 'industry' THEN 3
        ELSE 99
      END
    `
  );
  return result.rows;
}

export async function findRegionProgress(region, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT region, xp, level, updated_at
    FROM region_progress
    WHERE region = $1
    `,
    [region]
  );
  return result.rows[0] ?? null;
}

export async function findSellTotalsByCategory(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT category, COALESCE(SUM(total_price), 0) AS total_price
    FROM shop_transactions
    WHERE transaction_type = 'sell'
      AND category IN ('farming', 'fishing', 'mining')
    GROUP BY category
    `
  );
  return result.rows;
}

export async function upsertRegionProgress(region, xp, level, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO region_progress (region, xp, level, updated_at)
    VALUES ($1, $2, $3, NOW())
    ON CONFLICT (region)
    DO UPDATE
    SET
      xp = EXCLUDED.xp,
      level = EXCLUDED.level,
      updated_at = NOW()
    RETURNING region, xp, level, updated_at
    `,
    [region, xp, level]
  );
  return result.rows[0];
}
