import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function findAllStocks(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      current_price,
      previous_price,
      volatility_type,
      min_price,
      max_price,
      created_at,
      updated_at
    FROM stock
    ORDER BY name ASC
    `
  );
  return result.rows;
}

export async function findStockById(stockId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      current_price,
      previous_price,
      volatility_type,
      min_price,
      max_price,
      created_at,
      updated_at
    FROM stock
    WHERE id = $1
    `,
    [stockId]
  );
  return result.rows[0] ?? null;
}

export async function findStockByIdForUpdate(stockId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      current_price,
      previous_price,
      volatility_type,
      min_price,
      max_price,
      created_at,
      updated_at
    FROM stock
    WHERE id = $1
    FOR UPDATE
    `,
    [stockId]
  );
  return result.rows[0] ?? null;
}

export async function updateStockPrice(
  { stockId, previousPrice, currentPrice },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE stock
    SET
      previous_price = $2,
      current_price = $3,
      updated_at = NOW()
    WHERE id = $1
    RETURNING
      id,
      name,
      current_price,
      previous_price,
      volatility_type,
      min_price,
      max_price,
      created_at,
      updated_at
    `,
    [stockId, previousPrice, currentPrice]
  );
  return result.rows[0] ?? null;
}

export async function insertStockPriceHistory(
  { stockId, price, recordedAt = null },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO stock_price_history (stock_id, price, recorded_at)
    VALUES ($1, $2, COALESCE($3, NOW()))
    RETURNING id, stock_id, price, recorded_at
    `,
    [stockId, price, recordedAt]
  );
  return result.rows[0];
}

export async function findPlayerStockPosition(playerId, stockId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      player_id,
      stock_id,
      quantity,
      avg_buy_price,
      updated_at
    FROM player_stock
    WHERE player_id = $1
      AND stock_id = $2
    `,
    [playerId, stockId]
  );
  return result.rows[0] ?? null;
}

export async function findPlayerStockPositionForUpdate(playerId, stockId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      player_id,
      stock_id,
      quantity,
      avg_buy_price,
      updated_at
    FROM player_stock
    WHERE player_id = $1
      AND stock_id = $2
    FOR UPDATE
    `,
    [playerId, stockId]
  );
  return result.rows[0] ?? null;
}

export async function upsertPlayerStockPosition(
  { playerId, stockId, quantity, avgBuyPrice },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO player_stock (player_id, stock_id, quantity, avg_buy_price, updated_at)
    VALUES ($1, $2, $3, $4, NOW())
    ON CONFLICT (player_id, stock_id)
    DO UPDATE SET
      quantity = EXCLUDED.quantity,
      avg_buy_price = EXCLUDED.avg_buy_price,
      updated_at = NOW()
    RETURNING
      player_id,
      stock_id,
      quantity,
      avg_buy_price,
      updated_at
    `,
    [playerId, stockId, quantity, avgBuyPrice]
  );
  return result.rows[0] ?? null;
}

export async function deletePlayerStockPosition(playerId, stockId, executor) {
  const db = getExecutor(executor);
  await db.query(
    `
    DELETE FROM player_stock
    WHERE player_id = $1
      AND stock_id = $2
    `,
    [playerId, stockId]
  );
}

export async function listPlayerStockPositions(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      ps.player_id,
      ps.stock_id,
      ps.quantity,
      ps.avg_buy_price,
      ps.updated_at
    FROM player_stock ps
    WHERE ps.player_id = $1
    ORDER BY ps.updated_at DESC
    `,
    [playerId]
  );
  return result.rows;
}

export async function listStocksWithPlayerPosition(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      s.id,
      s.name,
      s.current_price,
      s.previous_price,
      s.volatility_type,
      s.min_price,
      s.max_price,
      s.created_at,
      s.updated_at,
      COALESCE(ps.quantity, 0)::INTEGER AS quantity,
      COALESCE(ps.avg_buy_price, 0)::INTEGER AS avg_buy_price
    FROM stock s
    LEFT JOIN player_stock ps
      ON ps.stock_id = s.id
      AND ps.player_id = $1
    ORDER BY s.name ASC
    `,
    [playerId]
  );
  return result.rows;
}
