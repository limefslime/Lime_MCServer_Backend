import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function ensurePlayerExists(playerId, executor) {
  const db = getExecutor(executor);
  await db.query(
    `
    INSERT INTO players (id, username)
    VALUES ($1, NULL)
    ON CONFLICT (id) DO NOTHING
    `,
    [playerId]
  );
}

export async function ensureWalletExists(playerId, executor) {
  const db = getExecutor(executor);
  await db.query(
    `
    INSERT INTO wallets (player_id, balance)
    VALUES ($1, 0)
    ON CONFLICT (player_id) DO NOTHING
    `,
    [playerId]
  );
}

export async function findWalletByPlayerId(playerId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT player_id, balance
    FROM wallets
    WHERE player_id = $1
    `,
    [playerId]
  );
  return result.rows[0] ?? null;
}

export async function addBalance(playerId, amount, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE wallets
    SET balance = balance + $2,
        updated_at = NOW()
    WHERE player_id = $1
    RETURNING player_id, balance
    `,
    [playerId, amount]
  );
  return result.rows[0] ?? null;
}

/**
 * 잔액이 충분할 때만 차감한다.
 * 조건을 SQL에서 처리해 동시성 상황에서도 음수 잔액을 방지한다.
 */
export async function subtractBalanceIfEnough(playerId, amount, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE wallets
    SET balance = balance - $2,
        updated_at = NOW()
    WHERE player_id = $1
      AND balance >= $2
    RETURNING player_id, balance
    `,
    [playerId, amount]
  );
  return result.rows[0] ?? null;
}

export async function insertLedgerEntry(
  { playerId, targetPlayerId = null, type, amount, reason },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO ledger (player_id, target_player_id, type, amount, reason)
    VALUES ($1, $2, $3, $4, $5)
    RETURNING id, player_id, target_player_id, type, amount, reason, created_at
    `,
    [playerId, targetPlayerId, type, amount, reason]
  );
  return result.rows[0];
}
