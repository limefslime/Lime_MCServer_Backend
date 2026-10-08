import { readFile } from 'node:fs/promises';
import { withTransaction } from '../../db/pool.js';
import { ensurePlayerExists, ensureWalletExists, insertLedgerEntry } from '../wallet/wallet.repository.js';
import { validateReward, rewardDecision, RewardError } from './reward.core.js';

export async function grantQuestReward(input, transaction = withTransaction) {
  // Read policies on each claim so administrators can change values without restart.
  const path = process.env.S2_REWARD_POLICY_PATH || new URL('../../../config/s2-rewards.json', import.meta.url);
  const policies = JSON.parse(await readFile(path, 'utf8'));
  const claim = validateReward(input, policies);
  const { playerId, requestId, rewardId, amount, cooldownSeconds } = claim;
  return transaction(async (client) => {
    await ensurePlayerExists(playerId, client);
    await ensureWalletExists(playerId, client);
    // Serialize all rewards for this UUID; the database lock works across API workers.
    await client.query('SELECT player_id FROM wallets WHERE player_id=$1 FOR UPDATE', [playerId]);
    const receipt = await client.query(
      'SELECT reward_id, result FROM integration_reward_receipts WHERE player_id=$1 AND request_id=$2',
      [playerId, requestId]);
    if (receipt.rows.length) {
      if (receipt.rows[0].reward_id !== rewardId) throw new RewardError('requestId reused for another reward', 409);
      return { ...receipt.rows[0].result, replayed: true };
    }
    const state = await client.query(
      'SELECT last_granted_at FROM integration_reward_state WHERE player_id=$1 AND reward_id=$2',
      [playerId, rewardId]);
    const { rows: [clock] } = await client.query('SELECT clock_timestamp() AS now');
    const decision = rewardDecision(state.rows[0]?.last_granted_at, cooldownSeconds, clock.now);
    let result = { playerId, requestId, rewardId, granted: false, ...decision };
    delete result.grant;
    if (decision.grant) {
      const balance = await client.query(
        `UPDATE wallets SET balance=balance+$2, updated_at=NOW()
         WHERE player_id=$1 AND balance::bigint+$2 <= 2147483647 RETURNING balance`, [playerId, amount]);
      if (!balance.rows.length) throw new RewardError('wallet balance limit exceeded', 409);
      const ledger = await insertLedgerEntry({ playerId, type: 'add', amount, reason: 'quest_reward' }, client);
      await client.query(
        `INSERT INTO integration_reward_state(player_id,reward_id,last_granted_at) VALUES($1,$2,$3)
         ON CONFLICT(player_id,reward_id) DO UPDATE SET last_granted_at=EXCLUDED.last_granted_at`,
        [playerId, rewardId, clock.now]);
      result = { playerId, requestId, rewardId, granted: true, amount, balance: Number(balance.rows[0].balance),
        ledgerId: String(ledger.id), grantedAt: clock.now.toISOString() };
    }
    await client.query(
      'INSERT INTO integration_reward_receipts(player_id,request_id,reward_id,result) VALUES($1,$2,$3,$4)',
      [playerId, requestId, rewardId, result]);
    await client.query('INSERT INTO integration_audit_outbox(payload) VALUES($1)',
      [{ event: 'quest_reward', ...result }]);
    return result;
  });
}
