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

function buildShopModifierBreakdown({ focus, projectEffects, events }) {
  const focusMultiplier = Number(focus?.multiplier ?? 1);
  const projectEffectsMultiplier = Number(projectEffects?.multiplier ?? 1);
  const eventsMultiplier = Number(events?.multiplier ?? 1);

  return {
    focus: {
      applied: Boolean(focus?.applied),
      target: focus?.target ?? null,
      multiplier: Number.isFinite(focusMultiplier) && focusMultiplier > 0 ? focusMultiplier : 1,
    },
    projectEffects: {
      applied: Boolean(projectEffects?.applied),
      count: Number.isInteger(projectEffects?.count) ? projectEffects.count : 0,
      multiplier:
        Number.isFinite(projectEffectsMultiplier) && projectEffectsMultiplier > 0
          ? projectEffectsMultiplier
          : 1,
      targets: Array.isArray(projectEffects?.targets) ? projectEffects.targets : [],
    },
    events: {
      applied: Boolean(events?.applied),
      count: Number.isInteger(events?.count) ? events.count : 0,
      multiplier:
        Number.isFinite(eventsMultiplier) && eventsMultiplier > 0 ? eventsMultiplier : 1,
      regions: Array.isArray(events?.regions) ? events.regions : [],
    },
    totalMultiplier:
      (Number.isFinite(focusMultiplier) && focusMultiplier > 0 ? focusMultiplier : 1) *
      (Number.isFinite(projectEffectsMultiplier) && projectEffectsMultiplier > 0
        ? projectEffectsMultiplier
        : 1) *
      (Number.isFinite(eventsMultiplier) && eventsMultiplier > 0 ? eventsMultiplier : 1),
  };
}

export function calculateShopModifiers({
  activeEvents,
  focusState,
  projectEffects,
  itemCategory,
}) {
  const safeActiveEvents = Array.isArray(activeEvents) ? activeEvents : [];
  const safeProjectEffects = Array.isArray(projectEffects) ? projectEffects : [];
  const safeFocusState = typeof focusState === "string" ? focusState : null;

  let buyPriceMultiplier = 1;
  let sellPriceMultiplier = 1;
  const extraRewardMultiplier = 1;

  if (typeof itemCategory !== "string" || itemCategory.trim().length === 0) {
    return {
      buyPriceMultiplier,
      sellPriceMultiplier,
      extraRewardMultiplier,
      breakdown: buildShopModifierBreakdown({
        focus: resolveFocusModifierInfo(null, null),
        projectEffects: resolveProjectEffectModifierInfo([], null),
        events: resolveEventModifierInfo([], null),
      }),
    };
  }

  const safeItemCategory = itemCategory.trim();

  if (safeItemCategory === "misc") {
    return {
      buyPriceMultiplier,
      sellPriceMultiplier,
      extraRewardMultiplier,
      breakdown: buildShopModifierBreakdown({
        focus: resolveFocusModifierInfo(null, null),
        projectEffects: resolveProjectEffectModifierInfo([], safeItemCategory),
        events: resolveEventModifierInfo([], safeItemCategory),
      }),
    };
  }

  const focusInfo = resolveFocusModifierInfo(safeFocusState, safeItemCategory);
  const projectEffectsInfo = resolveProjectEffectModifierInfo(safeProjectEffects, safeItemCategory);
  const eventInfo = resolveEventModifierInfo(safeActiveEvents, safeItemCategory);
  const breakdown = buildShopModifierBreakdown({
    focus: focusInfo,
    projectEffects: projectEffectsInfo,
    events: eventInfo,
  });

  buyPriceMultiplier *= breakdown.totalMultiplier;
  sellPriceMultiplier *= breakdown.totalMultiplier;

  return {
    buyPriceMultiplier,
    sellPriceMultiplier,
    extraRewardMultiplier,
    breakdown,
  };
}
