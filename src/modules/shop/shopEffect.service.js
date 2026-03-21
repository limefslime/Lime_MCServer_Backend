const FOCUS_PRICE_MULTIPLIER = 1.1;
const PRICE_BONUS_EFFECT_TYPE = "price_bonus";

function normalizeEffectValue(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue) || numericValue <= 0) {
    return 0;
  }
  return numericValue;
}

function isPriceBonusProjectEffect(effect, itemCategory) {
  if (!effect || typeof effect !== "object") {
    return false;
  }

  return (
    effect.is_active === true &&
    effect.effect_type === PRICE_BONUS_EFFECT_TYPE &&
    effect.effect_target === itemCategory
  );
}

function isPriceBonusEvent(event, itemCategory) {
  if (!event || typeof event !== "object") {
    return false;
  }

  return (
    event.effect_type === PRICE_BONUS_EFFECT_TYPE &&
    (event.region === itemCategory || event.region === "global")
  );
}

function resolveFocusModifierInfo(focusState, itemCategory) {
  const applied = Boolean(focusState && itemCategory === focusState);
  return {
    applied,
    target: applied ? focusState : null,
    multiplier: applied ? FOCUS_PRICE_MULTIPLIER : 1,
  };
}

function resolveProjectEffectModifierInfo(projectEffects, itemCategory) {
  let multiplier = 1;
  let count = 0;
  const targetSet = new Set();

  for (const effect of projectEffects ?? []) {
    if (!isPriceBonusProjectEffect(effect, itemCategory)) {
      continue;
    }

    const effectValue = normalizeEffectValue(effect.effect_value);
    if (effectValue <= 0) {
      continue;
    }

    multiplier *= 1 + effectValue;
    count += 1;
    if (typeof effect.effect_target === "string") {
      targetSet.add(effect.effect_target);
    }
  }

  return {
    applied: count > 0,
    count,
    multiplier,
    targets: [...targetSet],
  };
}

function resolveEventModifierInfo(activeEvents, itemCategory) {
  let multiplier = 1;
  let count = 0;
  const regionSet = new Set();

  for (const event of activeEvents ?? []) {
    if (!isPriceBonusEvent(event, itemCategory)) {
      continue;
    }

    const effectValue = normalizeEffectValue(event.effect_value);
    if (effectValue <= 0) {
      continue;
    }

    multiplier *= 1 + effectValue;
    count += 1;
    if (typeof event.region === "string") {
      regionSet.add(event.region);
    }
  }

  return {
    applied: count > 0,
    count,
    multiplier,
    regions: [...regionSet],
  };
}

function normalizeRate(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 0;
  }
  return Math.min(0.5, numeric);
}

function normalizeMultiplier(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return 1;
  }
  return numeric;
}

function resolveVillageModifierInfo(villageEffect) {
  const discountRate = normalizeRate(villageEffect?.discountRate);
  const buyMultiplier = normalizeMultiplier(villageEffect?.buyPriceMultiplier);
  const level = Number.isInteger(Number(villageEffect?.level))
    ? Math.max(1, Number(villageEffect.level))
    : 1;

  return {
    applied: Boolean(villageEffect?.applied) && discountRate > 0,
    level,
    discountRate,
    buyMultiplier,
  };
}

function buildShopModifierBreakdown({ focus, projectEffects, events, village }) {
  const focusMultiplier = normalizeMultiplier(focus?.multiplier);
  const projectEffectsMultiplier = normalizeMultiplier(projectEffects?.multiplier);
  const eventsMultiplier = normalizeMultiplier(events?.multiplier);
  const villageBuyMultiplier = normalizeMultiplier(village?.buyMultiplier);

  const totalSellMultiplier =
    focusMultiplier * projectEffectsMultiplier * eventsMultiplier;
  const totalBuyMultiplier = totalSellMultiplier * villageBuyMultiplier;

  return {
    focus: {
      applied: Boolean(focus?.applied),
      target: focus?.target ?? null,
      multiplier: focusMultiplier,
    },
    projectEffects: {
      applied: Boolean(projectEffects?.applied),
      count: Number.isInteger(projectEffects?.count) ? projectEffects.count : 0,
      multiplier: projectEffectsMultiplier,
      targets: Array.isArray(projectEffects?.targets) ? projectEffects.targets : [],
    },
    events: {
      applied: Boolean(events?.applied),
      count: Number.isInteger(events?.count) ? events.count : 0,
      multiplier: eventsMultiplier,
      regions: Array.isArray(events?.regions) ? events.regions : [],
    },
    village: {
      applied: Boolean(village?.applied),
      level: Number.isInteger(village?.level) ? village.level : 1,
      discountRate: normalizeRate(village?.discountRate),
      buyMultiplier: villageBuyMultiplier,
    },
    totalBuyMultiplier,
    totalSellMultiplier,
    totalMultiplier: totalBuyMultiplier,
  };
}

export function calculateShopModifiers({
  activeEvents,
  focusState,
  projectEffects,
  villageEffect,
  itemCategory,
}) {
  const safeActiveEvents = Array.isArray(activeEvents) ? activeEvents : [];
  const safeProjectEffects = Array.isArray(projectEffects) ? projectEffects : [];
  const safeFocusState = typeof focusState === "string" ? focusState : null;
  const villageInfo = resolveVillageModifierInfo(villageEffect);

  let buyPriceMultiplier = 1;
  let sellPriceMultiplier = 1;
  const extraRewardMultiplier = 1;

  if (typeof itemCategory !== "string" || itemCategory.trim().length === 0) {
    const breakdown = buildShopModifierBreakdown({
      focus: resolveFocusModifierInfo(null, null),
      projectEffects: resolveProjectEffectModifierInfo([], null),
      events: resolveEventModifierInfo([], null),
      village: villageInfo,
    });
    return {
      buyPriceMultiplier: buyPriceMultiplier * breakdown.totalBuyMultiplier,
      sellPriceMultiplier: sellPriceMultiplier * breakdown.totalSellMultiplier,
      extraRewardMultiplier,
      breakdown,
    };
  }

  const safeItemCategory = itemCategory.trim();
  const focusInfo = resolveFocusModifierInfo(safeFocusState, safeItemCategory);
  const projectEffectsInfo = resolveProjectEffectModifierInfo(safeProjectEffects, safeItemCategory);
  const eventInfo = resolveEventModifierInfo(safeActiveEvents, safeItemCategory);
  const breakdown = buildShopModifierBreakdown({
    focus: focusInfo,
    projectEffects: projectEffectsInfo,
    events: eventInfo,
    village: villageInfo,
  });

  buyPriceMultiplier *= breakdown.totalBuyMultiplier;
  sellPriceMultiplier *= breakdown.totalSellMultiplier;

  return {
    buyPriceMultiplier,
    sellPriceMultiplier,
    extraRewardMultiplier,
    breakdown,
  };
}
