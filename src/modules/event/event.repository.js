import pool from "../../db/pool.js";

function getExecutor(executor) {
  return executor ?? pool;
}

export async function createEvent(
  { name, description, region, effectType, effectValue, startsAt = null, endsAt = null },
  executor
) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    INSERT INTO events (
      name,
      description,
      region,
      effect_type,
      effect_value,
      starts_at,
      ends_at
    )
    VALUES ($1, $2, $3, $4, $5, $6, $7)
    RETURNING
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    `,
    [name, description, region, effectType, effectValue, startsAt, endsAt]
  );
  return result.rows[0];
}

export async function getAllEvents(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    FROM events
    ORDER BY created_at DESC
    `
  );
  return result.rows;
}

export async function getEventById(eventId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    FROM events
    WHERE id = $1
    `,
    [eventId]
  );
  return result.rows[0] ?? null;
}

export async function activateEvent(eventId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE events
    SET
      status = 'active',
      starts_at = NOW()
    WHERE id = $1
      AND status = 'draft'
    RETURNING
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    `,
    [eventId]
  );
  return result.rows[0] ?? null;
}

export async function endEvent(eventId, executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    UPDATE events
    SET
      status = 'ended',
      ends_at = NOW()
    WHERE id = $1
      AND status = 'active'
    RETURNING
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    `,
    [eventId]
  );
  return result.rows[0] ?? null;
}

export async function getActiveEvents(executor) {
  const db = getExecutor(executor);
  const result = await db.query(
    `
    SELECT
      id,
      name,
      description,
      region,
      effect_type,
      effect_value,
      status,
      created_at,
      starts_at,
      ends_at
    FROM events
    WHERE
      (
        starts_at IS NULL
        AND ends_at IS NULL
        AND status = 'active'
      )
      OR
      (
        (starts_at IS NOT NULL OR ends_at IS NOT NULL)
        AND (starts_at IS NULL OR starts_at <= NOW())
        AND (ends_at IS NULL OR ends_at > NOW())
      )
    ORDER BY starts_at DESC NULLS LAST, created_at DESC
    `
  );
  return result.rows;
}
