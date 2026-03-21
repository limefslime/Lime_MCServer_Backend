import { updateStockPrices } from "./stockPrice.service.js";

const DEFAULT_INTERVAL_MS = 20000;

let timer = null;
let inFlight = false;

function parsePositiveInt(rawValue, fallback) {
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  const floored = Math.floor(parsed);
  if (floored <= 0) {
    return fallback;
  }
  return floored;
}

function isTickerEnabled() {
  const raw = process.env.INVEST_STOCK_TICK_ENABLED;
  if (raw == null) {
    return true;
  }

  const normalized = String(raw).trim().toLowerCase();
  if (normalized === "0" || normalized === "false" || normalized === "off") {
    return false;
  }
  return true;
}

function getIntervalMs() {
  return parsePositiveInt(process.env.INVEST_STOCK_TICK_INTERVAL_MS, DEFAULT_INTERVAL_MS);
}

async function runCycle({ logger }) {
  if (inFlight) {
    return;
  }
  inFlight = true;

  try {
    const updatedStocks = await updateStockPrices();
    if (updatedStocks.length > 0) {
      logger.info(`[invest.ticker] updated ${updatedStocks.length} stock price(s)`);
    }
  } catch (error) {
    logger.error("[invest.ticker] cycle failed", error);
  } finally {
    inFlight = false;
  }
}

export function startStockPriceTicker({ logger = console } = {}) {
  if (timer) {
    return { started: true, intervalMs: getIntervalMs() };
  }

  if (!isTickerEnabled()) {
    return { started: false, disabled: true };
  }

  const intervalMs = getIntervalMs();
  const run = () => {
    void runCycle({ logger });
  };

  timer = setInterval(run, intervalMs);
  if (typeof timer.unref === "function") {
    timer.unref();
  }
  run();

  return { started: true, intervalMs };
}

export function stopStockPriceTicker() {
  if (!timer) {
    return;
  }
  clearInterval(timer);
  timer = null;
}
