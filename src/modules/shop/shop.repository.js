import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function findAllShopItems(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      item_id,
      item_name,
      category,
      buy_price,
      sell_price,
      is_active,
      stock_quantity,
      replenish_amount,
      replenish_interval_seconds,
      last_replenished_at
    FROM shop_items
    ORDER BY id ASC
    `
  );
  return result.rows;
}

export async function findShopItemByItemId(itemId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      item_id,
      item_name,
      category,
      buy_price,
      sell_price,
      is_active,
      stock_quantity,
      replenish_amount,
      replenish_interval_seconds,
      last_replenished_at
    FROM shop_items
    WHERE item_id = $1
    `,
    [itemId]
  );
  return result.rows[0] ?? null;
}

export async function tryReplenishShopItemStock(itemId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE shop_items
    SET
      stock_quantity = LEAST(2147483647, stock_quantity + replenish_amount),
      last_replenished_at = NOW(),
      updated_at = NOW()
    WHERE item_id = $1
      AND stock_quantity <= 0
      AND replenish_amount > 0
      AND replenish_interval_seconds > 0
      AND NOW() >= last_replenished_at + make_interval(secs => replenish_interval_seconds)
    RETURNING
      item_id,
      item_name,
      category,
      buy_price,
      sell_price,
      is_active,
      stock_quantity,
      replenish_amount,
      replenish_interval_seconds,
      last_replenished_at
    `,
    [itemId]
  );
  return result.rows[0] ?? null;
}

export async function decreaseShopItemStockIfEnough(itemId, quantity, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE shop_items
    SET
      stock_quantity = stock_quantity - $2,
      updated_at = NOW()
    WHERE item_id = $1
      AND stock_quantity >= $2
    RETURNING
      item_id,
      item_name,
      category,
      buy_price,
      sell_price,
      is_active,
      stock_quantity,
      replenish_amount,
      replenish_interval_seconds,
      last_replenished_at
    `,
    [itemId, quantity]
  );
  return result.rows[0] ?? null;
}

export async function increaseShopItemStock(itemId, quantity, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE shop_items
    SET
      stock_quantity = LEAST(2147483647, stock_quantity + $2),
      updated_at = NOW()
    WHERE item_id = $1
    RETURNING
      item_id,
      item_name,
      category,
      buy_price,
      sell_price,
      is_active,
      stock_quantity,
      replenish_amount,
      replenish_interval_seconds,
      last_replenished_at
    `,
    [itemId, quantity]
  );
  return result.rows[0] ?? null;
}

export async function findLatestShopTransactionByPlayerAndItem(playerId, itemId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT transaction_type, created_at
    FROM shop_transactions
    WHERE player_id = $1
      AND item_id = $2
    ORDER BY created_at DESC
    LIMIT 1
    `,
    [playerId, itemId]
  );
  return result.rows[0] ?? null;
}

export async function replenishDueShopItemStocks(limit = 200, executor) {
  const db = getExecutor(executor);
  const safeLimit =
    Number.isInteger(limit) && limit > 0 ? Math.min(1000, limit) : 200;

  const result = await db.query(
    `
    WITH due AS (
      SELECT id
      FROM shop_items
      WHERE stock_quantity <= 0
        AND replenish_amount > 0
        AND replenish_interval_seconds > 0
        AND NOW() >= last_replenished_at + make_interval(secs => replenish_interval_seconds)
      ORDER BY last_replenished_at ASC, id ASC
      LIMIT $1
      FOR UPDATE SKIP LOCKED
    )
    UPDATE shop_items AS item
    SET
      stock_quantity = LEAST(2147483647, item.stock_quantity + item.replenish_amount),
      last_replenished_at = NOW(),
      updated_at = NOW()
    FROM due
    WHERE item.id = due.id
    RETURNING
      item.item_id,
      item.stock_quantity,
      item.replenish_amount,
      item.replenish_interval_seconds,
      item.last_replenished_at
    `,
    [safeLimit]
  );

  return result.rows;
}

export async function insertShopTransaction(
  { playerId, itemId, category, quantity, unitPrice, totalPrice, transactionType },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO shop_transactions (
      player_id,
      item_id,
      category,
      quantity,
      unit_price,
      total_price,
      transaction_type
    )
    VALUES ($1, $2, $3, $4, $5, $6, $7)
    RETURNING
      id,
      player_id,
      item_id,
      category,
      quantity,
      unit_price,
      total_price,
      transaction_type,
      created_at
    `,
    [playerId, itemId, category, quantity, unitPrice, totalPrice, transactionType]
  );
  return result.rows[0];
}
