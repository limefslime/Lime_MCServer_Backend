import {
  activateEvent as activateEventRow,
  createEvent as createEventRow,
  endEvent as endEventRow,
  getActiveEvents as getActiveEventRows,
  getAllEvents,
  getEventById,
} from "./event.repository.js";

class EventServiceError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "EventServiceError";
    this.code = code;
  }
}

export const eventErrorCode = {
  INVALID_INPUT: "INVALID_INPUT",
  EVENT_NOT_FOUND: "EVENT_NOT_FOUND",
  INVALID_STATUS_TRANSITION: "INVALID_STATUS_TRANSITION",
};

const EVENT_REGIONS = new Set(["agri", "port", "industry", "global"]);
const EFFECT_TYPES = new Set(["price_bonus", "xp_bonus"]);
const EVENT_STATUSES = new Set(["draft", "active", "ended"]);
const EVENT_RUNTIME_STATUS = {
  SCHEDULED: "scheduled",
  ACTIVE: "active",
  EXPIRED: "expired",
};

export function isEventServiceError(error) {
  return error instanceof EventServiceError;
}

function validateUuid(value, fieldName) {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (typeof value !== "string" || !uuidRegex.test(value)) {
    throw new EventServiceError(eventErrorCode.INVALID_INPUT, `${fieldName} must be a valid uuid`);
  }
}

function validateEventId(eventId) {
  validateUuid(eventId, "eventId");
}

function validateRegion(region) {
  if (!EVENT_REGIONS.has(region)) {
    throw new EventServiceError(
      eventErrorCode.INVALID_INPUT,
      "region must be agri, port, industry, or global"
    );
  }
}

function validateEffectType(effectType) {
  if (!EFFECT_TYPES.has(effectType)) {
    throw new EventServiceError(
      eventErrorCode.INVALID_INPUT,
      "effectType must be price_bonus or xp_bonus"
    );
  }
}

function validateEffectValue(effectValue) {
  if (typeof effectValue !== "number" || !Number.isFinite(effectValue)) {
    throw new EventServiceError(eventErrorCode.INVALID_INPUT, "effectValue must be a number");
  }
  if (effectValue <= 0) {
    throw new EventServiceError(eventErrorCode.INVALID_INPUT, "effectValue must be greater than 0");
  }
}

function validateStatus(status) {
  if (!EVENT_STATUSES.has(status)) {
    throw new Error("invalid event row");
  }
}

function validateCreatePayload(payload) {
  if (!payload || typeof payload !== "object") {
    throw new EventServiceError(eventErrorCode.INVALID_INPUT, "request body is required");
  }
  if (typeof payload.name !== "string" || payload.name.trim().length === 0) {
    throw new EventServiceError(eventErrorCode.INVALID_INPUT, "name is required");
  }

  validateRegion(payload.region);
  validateEffectType(payload.effectType);
  validateEffectValue(payload.effectValue);
}

function normalizeEffectValue(value) {
  return Number(value);
}

function normalizeDateOrNull(value) {
  if (!value) {
    return null;
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }

  return parsed;
}

function resolveEventRuntimeStatus(row, now = new Date()) {
  const startsAt = normalizeDateOrNull(row.starts_at);
  const endsAt = normalizeDateOrNull(row.ends_at);

  if (row.status === "ended") {
    return EVENT_RUNTIME_STATUS.EXPIRED;
  }

  if (row.status === "draft") {
    return EVENT_RUNTIME_STATUS.SCHEDULED;
  }

  if (row.status !== "active") {
    return EVENT_RUNTIME_STATUS.SCHEDULED;
  }

  if (!startsAt && !endsAt) {
    return EVENT_RUNTIME_STATUS.ACTIVE;
  }

  if (startsAt && now < startsAt) {
    return EVENT_RUNTIME_STATUS.SCHEDULED;
  }

  if (endsAt && now >= endsAt) {
    return EVENT_RUNTIME_STATUS.EXPIRED;
  }

  return EVENT_RUNTIME_STATUS.ACTIVE;
}

function isEventRuntimeActive(row, now = new Date()) {
  return resolveEventRuntimeStatus(row, now) === EVENT_RUNTIME_STATUS.ACTIVE;
}

function buildEventRuntimeFields(row, now = new Date()) {
  return {
    runtimeStatus: resolveEventRuntimeStatus(row, now),
    isRuntimeActive: isEventRuntimeActive(row, now),
    startsAt: row.starts_at ?? null,
    endsAt: row.ends_at ?? null,
  };
}

function buildEventAppliesFields(row) {
  const isGlobalEvent = row.region === "global";
  const isPriceBonusEvent = row.effect_type === "price_bonus";

  return {
    appliesToCategory: isGlobalEvent ? "global" : row.region,
    isGlobalEvent,
    isPriceBonusEvent,
  };
}

function assertEventRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasName = isObject && typeof row.name === "string" && row.name.trim().length > 0;
  const hasRegion = isObject && typeof row.region === "string";
  const hasEffectType = isObject && typeof row.effect_type === "string";
  const hasStatus = isObject && typeof row.status === "string";
  const numericEffectValue = isObject ? normalizeEffectValue(row.effect_value) : NaN;

  if (
    !isObject ||
    !hasId ||
    !hasName ||
    !hasRegion ||
    !hasEffectType ||
    !hasStatus ||
    !Number.isFinite(numericEffectValue)
  ) {
    throw new Error("invalid event row");
  }

  if (!EVENT_REGIONS.has(row.region) || !EFFECT_TYPES.has(row.effect_type)) {
    throw new Error("invalid event row");
  }
  validateStatus(row.status);
}

function assertActiveEventRow(row) {
  const isObject = typeof row === "object" && row !== null;
  const hasId = isObject && typeof row.id === "string" && row.id.trim().length > 0;
  const hasRegion = isObject && typeof row.region === "string";
  const hasEffectType = isObject && typeof row.effect_type === "string";
  const hasStatus = isObject && typeof row.status === "string";
  const numericEffectValue = isObject ? normalizeEffectValue(row.effect_value) : NaN;

  if (
    !isObject ||
    !hasId ||
    !hasRegion ||
    !hasEffectType ||
    !hasStatus ||
    !Number.isFinite(numericEffectValue)
  ) {
    throw new Error("invalid active event row");
  }

  if (!EVENT_REGIONS.has(row.region) || !EFFECT_TYPES.has(row.effect_type)) {
    throw new Error("invalid active event row");
  }
  validateStatus(row.status);
}

function buildEventListItem(row, now = new Date()) {
  assertEventRow(row);
  const runtimeFields = buildEventRuntimeFields(row, now);
  const appliesFields = buildEventAppliesFields(row);

  return {
    id: row.id,
    name: row.name,
    region: row.region,
    effectType: row.effect_type,
    effectValue: normalizeEffectValue(row.effect_value),
    status: row.status,
    runtimeStatus: runtimeFields.runtimeStatus,
    isRuntimeActive: runtimeFields.isRuntimeActive,
    startsAt: runtimeFields.startsAt,
    endsAt: runtimeFields.endsAt,
    appliesToCategory: appliesFields.appliesToCategory,
    isGlobalEvent: appliesFields.isGlobalEvent,
    isPriceBonusEvent: appliesFields.isPriceBonusEvent,
  };
}

function buildEventDetail(row, now = new Date()) {
  assertEventRow(row);
  const runtimeFields = buildEventRuntimeFields(row, now);
  const appliesFields = buildEventAppliesFields(row);

  return {
    id: row.id,
    name: row.name,
    description: row.description,
    region: row.region,
    effectType: row.effect_type,
    effectValue: normalizeEffectValue(row.effect_value),
    status: row.status,
    runtimeStatus: runtimeFields.runtimeStatus,
    isRuntimeActive: runtimeFields.isRuntimeActive,
    startsAt: runtimeFields.startsAt,
    endsAt: runtimeFields.endsAt,
    appliesToCategory: appliesFields.appliesToCategory,
    isGlobalEvent: appliesFields.isGlobalEvent,
    isPriceBonusEvent: appliesFields.isPriceBonusEvent,
  };
}

function buildActiveEventItem(row, now = new Date()) {
  assertActiveEventRow(row);
  void now;

  return {
    id: row.id,
    region: row.region,
    effect_type: row.effect_type,
    effect_value: normalizeEffectValue(row.effect_value),
    status: row.status,
  };
}

function buildStatusChangeResult(row, kind) {
  assertEventRow(row);
  const runtimeFields = buildEventRuntimeFields(row);

  if (kind === "activate") {
    return {
      id: row.id,
      status: row.status,
      runtimeStatus: runtimeFields.runtimeStatus,
      isRuntimeActive: runtimeFields.isRuntimeActive,
      startsAt: runtimeFields.startsAt,
      endsAt: runtimeFields.endsAt,
    };
  }

  return {
    id: row.id,
    status: row.status,
    runtimeStatus: runtimeFields.runtimeStatus,
    isRuntimeActive: runtimeFields.isRuntimeActive,
    startsAt: runtimeFields.startsAt,
    endsAt: runtimeFields.endsAt,
  };
}

async function getEventOrThrow(eventId, executor) {
  const row = await getEventById(eventId, executor);
  if (!row) {
    throw new EventServiceError(eventErrorCode.EVENT_NOT_FOUND, "event not found");
  }
  assertEventRow(row);
  return row;
}

function assertCanActivate(eventRow) {
  if (eventRow.status !== "draft") {
    throw new EventServiceError(
      eventErrorCode.INVALID_STATUS_TRANSITION,
      "only draft events can be activated"
    );
  }
}

function assertCanEnd(eventRow) {
  if (eventRow.status !== "active") {
    throw new EventServiceError(
      eventErrorCode.INVALID_STATUS_TRANSITION,
      "only active events can be ended"
    );
  }
}

export async function createEvent(payload) {
  validateCreatePayload(payload);

  const row = await createEventRow({
    name: payload.name.trim(),
    description: payload.description ?? null,
    region: payload.region,
    effectType: payload.effectType,
    effectValue: payload.effectValue,
    startsAt: null,
    endsAt: null,
  });

  return buildEventListItem(row);
}

export async function getEvents() {
  const rows = await getAllEvents();
  const now = new Date();
  return rows.map((row) => buildEventListItem(row, now));
}

export async function getEventDetail(eventId) {
  validateEventId(eventId);
  const row = await getEventOrThrow(eventId);
  return buildEventDetail(row, new Date());
}

export async function activateEvent(eventId) {
  validateEventId(eventId);

  const eventRow = await getEventOrThrow(eventId);
  assertCanActivate(eventRow);

  const updated = await activateEventRow(eventId);
  if (!updated) {
    throw new EventServiceError(
      eventErrorCode.INVALID_STATUS_TRANSITION,
      "only draft events can be activated"
    );
  }

  return buildStatusChangeResult(updated, "activate");
}

export async function endEvent(eventId) {
  validateEventId(eventId);

  const eventRow = await getEventOrThrow(eventId);
  assertCanEnd(eventRow);

  const updated = await endEventRow(eventId);
  if (!updated) {
    throw new EventServiceError(
      eventErrorCode.INVALID_STATUS_TRANSITION,
      "only active events can be ended"
    );
  }

  return buildStatusChangeResult(updated, "end");
}

export async function getActiveEvents() {
  const rows = await getActiveEventRows();
  const now = new Date();
  return rows.filter((row) => isEventRuntimeActive(row, now)).map((row) => buildActiveEventItem(row, now));
}
