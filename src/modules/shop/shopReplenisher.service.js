import { replenishDueShopItemStocks } from "./shop.repository.js";

const DEFAULT_INTERVAL_MS = 10000;
const DEFAULT_BATCH_LIMIT = 200;

let timer = null;
let inFlight = false;

function parsePositiveInt(rawValue, fallback) {
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  const intValue = Math.floor(parsed);
  if (intValue <= 0) {
    return fallback;
  }
  return intValue;
}

function isReplenisherEnabled() {
  const raw = process.env.SHOP_STOCK_REPLENISH_ENABLED;
  if (raw == null) {
    return true;
  }

  const normalized = String(raw).trim().toLowerCase();
  if (normalized === "0" || normalized === "false" || normalized === "off") {
    return false;
  }
  return true;
}

function getReplenishIntervalMs() {
  return parsePositiveInt(
    process.env.SHOP_STOCK_REPLENISH_INTERVAL_MS,
    DEFAULT_INTERVAL_MS
  );
}

function getBatchLimit() {
  return parsePositiveInt(
    process.env.SHOP_STOCK_REPLENISH_BATCH_LIMIT,
    DEFAULT_BATCH_LIMIT
  );
}

async function runReplenishCycle({ logger, batchLimit }) {
  if (inFlight) {
    return;
  }
  inFlight = true;

  try {
    const replenishedRows = await replenishDueShopItemStocks(batchLimit);
    if (replenishedRows.length > 0) {
      logger.info(
        `[shop.replenisher] replenished ${replenishedRows.length} item(s)`
      );
    }
  } catch (error) {
    logger.error("[shop.replenisher] cycle failed", error);
  } finally {
    inFlight = false;
  }
}

export function startShopStockReplenisher({ logger = console } = {}) {
  if (timer) {
    return { started: true, intervalMs: getReplenishIntervalMs() };
  }

  if (!isReplenisherEnabled()) {
    return { started: false, disabled: true };
  }

  const intervalMs = getReplenishIntervalMs();
  const batchLimit = getBatchLimit();

  const run = () => {
    void runReplenishCycle({ logger, batchLimit });
  };

  timer = setInterval(run, intervalMs);
  if (typeof timer.unref === "function") {
    timer.unref();
  }
  run();

  return { started: true, intervalMs, batchLimit };
}

export function stopShopStockReplenisher() {
  if (!timer) {
    return;
  }
  clearInterval(timer);
  timer = null;
}
