export class RewardError extends Error {
  constructor(message, status = 400) { super(message); this.status = status; }
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function validateReward(input, policies) {
  if (!input || !uuid.test(input.playerId) || !uuid.test(input.requestId)) {
    throw new RewardError('playerId and requestId must be UUIDs');
  }
  if (typeof input.rewardId !== 'string' || !/^[0-9A-F]{16}$/.test(input.rewardId)) {
    throw new RewardError('invalid rewardId');
  }
  const policy = Object.hasOwn(policies, input.rewardId) ? policies[input.rewardId] : null;
  if (!policy) throw new RewardError('unknown rewardId');
  if (!Number.isSafeInteger(policy.amount) || policy.amount <= 0 || policy.amount > 2147483647
      || !Number.isSafeInteger(policy.cooldownSeconds) || policy.cooldownSeconds < 0) {
    throw new RewardError('invalid server reward policy', 503);
  }
  if (Object.hasOwn(input, 'amount')) throw new RewardError('amount is controlled by server policy');
  return { playerId: input.playerId.toLowerCase(), requestId: input.requestId.toLowerCase(),
    rewardId: input.rewardId, ...policy };
}

// The caller locks the player's wallet before invoking this function. Different
// request IDs for the same reward are also subject to this cooldown decision.
export function rewardDecision(lastGranted, cooldownSeconds, now) {
  if (!lastGranted) return { grant: true };
  if (cooldownSeconds === 0) return { grant: false, reason: 'already_claimed' };
  const next = new Date(lastGranted).getTime() + cooldownSeconds * 1000;
  return now.getTime() >= next ? { grant: true }
    : { grant: false, reason: 'cooldown', nextEligibleAt: new Date(next).toISOString() };
}
