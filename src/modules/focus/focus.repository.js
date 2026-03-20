import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function findCurrentFocusState(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      focus_region,
      'active'::TEXT AS status,
      calculated_at
    FROM focus_state
    WHERE id = 1
    `
  );
  return result.rows[0] ?? null;
}

export async function upsertFocusState(focusRegion, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO focus_state (id, focus_region, calculated_at)
    VALUES (1, $1, NOW())
    ON CONFLICT (id)
    DO UPDATE
    SET
      focus_region = EXCLUDED.focus_region,
      calculated_at = NOW()
    RETURNING
      id,
      focus_region,
      'active'::TEXT AS status,
      calculated_at
    `,
    [focusRegion]
  );
  return result.rows[0];
}

export async function findRecentSellTotalsByCategory(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT category, COALESCE(SUM(total_price), 0) AS total_price
    FROM shop_transactions
    WHERE transaction_type = 'sell'
      AND created_at >= NOW() - INTERVAL '24 hours'
      AND category IN ('mining', 'farming', 'fishing', 'misc')
    GROUP BY category
    `
  );
  return result.rows;
}
