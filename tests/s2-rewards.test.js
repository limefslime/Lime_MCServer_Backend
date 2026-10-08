import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, mkdtemp, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { PGlite } from '@electric-sql/pglite';
import { grantQuestReward } from '../src/modules/integration/reward.service.js';
import { validateReward } from '../src/modules/integration/reward.core.js';
import { requireIntegrationToken } from '../src/modules/integration/integration.routes.js';
import { flushIntegrationAudit } from '../src/services/integrationAudit.js';

let db, directory;
const transact = work => db.transaction(client => work(client));
const claim = (rewardId = '2ABBEEB3F9BF1017', playerId = randomUUID()) =>
  ({ playerId, requestId: randomUUID(), rewardId });
before(async () => {
  db = new PGlite();
  // PGlite has core gen_random_uuid; only the unavailable pgcrypto extension
  // statement is excluded. Production migrations are otherwise exercised.
  await db.exec((await readFile(new URL('../src/migrations/001_create_wallet_core.sql', import.meta.url), 'utf8'))
    .replace('CREATE EXTENSION IF NOT EXISTS pgcrypto;', ''));
  await db.exec(await readFile(new URL('../src/migrations/019_add_integration_rewards.sql', import.meta.url), 'utf8'));
  directory = await mkdtemp(join(tmpdir(), 's2-audit-'));
  process.env.AUDIT_LOG_DIR = directory;
});
after(async () => { await db.close(); await rm(directory, { recursive: true }); delete process.env.AUDIT_LOG_DIR; });

test('simultaneous retries grant once; distinct request IDs cannot bypass one-time policy', async () => {
  const input = claim();
  const results = await Promise.all(Array.from({ length: 8 }, () => grantQuestReward(input, transact)));
  assert.equal(results.filter(r => !r.replayed).length, 1);
  assert.equal(results[0].amount, 70);
  const again = await grantQuestReward({ ...input, requestId: randomUUID() }, transact);
  assert.equal(again.granted, false);
  assert.equal(again.reason, 'already_claimed');
  const { rows: [wallet] } = await db.query('SELECT balance FROM wallets WHERE player_id=$1', [input.playerId]);
  assert.equal(wallet.balance, 70);
  const { rows: [ledger] } = await db.query('SELECT count(*) AS count FROM ledger WHERE player_id=$1', [input.playerId]);
  assert.equal(Number(ledger.count), 1);
});
test('same request ID for a different reward is rejected without paying', async () => {
  const input = claim(); await grantQuestReward(input, transact);
  await assert.rejects(grantQuestReward({ ...input, rewardId: '1966EA9C7559CBDE' }, transact), /reused/);
});
test('repeat rewards enforce elapsed cooldown, then allow the next claim', async () => {
  const input = claim('4146855A35C7CEEB');
  assert.equal((await grantQuestReward(input, transact)).amount, 100);
  assert.equal((await grantQuestReward({ ...input, requestId: randomUUID() }, transact)).reason, 'cooldown');
  await db.query("UPDATE integration_reward_state SET last_granted_at=NOW()-INTERVAL '2 days' WHERE player_id=$1", [input.playerId]);
  assert.equal((await grantQuestReward({ ...input, requestId: randomUUID() }, transact)).granted, true);
});
test('failure after wallet/ledger writes rolls back everything and permits retry', async () => {
  const input = claim();
  const failing = work => db.transaction(client => work({ query: (sql, args) => {
    if (sql.startsWith('INSERT INTO integration_audit_outbox')) throw new Error('simulated storage failure');
    return client.query(sql, args);
  } }));
  await assert.rejects(grantQuestReward(input, failing), /storage failure/);
  assert.equal((await db.query('SELECT * FROM wallets WHERE player_id=$1', [input.playerId])).rows.length, 0);
  assert.equal((await grantQuestReward(input, transact)).amount, 70);
});
test('wallet overflow does not consume claim or insert ledger', async () => {
  const input = claim();
  await db.query('INSERT INTO players(id) VALUES($1)', [input.playerId]);
  await db.query('INSERT INTO wallets(player_id,balance) VALUES($1,2147483647)', [input.playerId]);
  await assert.rejects(grantQuestReward(input, transact), /balance limit/);
  assert.equal((await db.query('SELECT * FROM integration_reward_receipts WHERE player_id=$1', [input.playerId])).rows.length, 0);
});
test('caller cannot set reward amount or claim an unknown ID', () => {
  const policies = { '2ABBEEB3F9BF1017': { amount: 70, cooldownSeconds: 0 } };
  assert.throws(() => validateReward({ ...claim(), amount: 999999 }, policies), /controlled/);
  assert.throws(() => validateReward(claim('FFFFFFFFFFFFFFFF'), policies), /unknown/);
});
test('integration endpoint fails closed without the configured token', () => {
  const previous = process.env.INTEGRATION_API_TOKEN;
  const invoke = value => {
    let status, continued = false;
    requireIntegrationToken({ get: () => value }, { status(n) { status=n; return this; }, json() {} }, () => { continued=true; });
    return { status, continued };
  };
  try {
    delete process.env.INTEGRATION_API_TOKEN;
    assert.equal(invoke('').status, 503);
    process.env.INTEGRATION_API_TOKEN = 'x'.repeat(32);
    assert.equal(invoke('Bearer wrong').status, 401);
    assert.equal(invoke('Bearer ' + 'x'.repeat(32)).continued, true);
  } finally {
    if (previous === undefined) delete process.env.INTEGRATION_API_TOKEN;
    else process.env.INTEGRATION_API_TOKEN = previous;
  }
});
test('audit outbox writes local JSONL once and retains IDs for crash deduplication', async () => {
  await flushIntegrationAudit(transact);
  const files = await readdir(directory);
  assert.ok(files.length > 0);
  const contents = await readFile(join(directory, files[0]), 'utf8');
  const rows = contents.trim().split('\n').map(JSON.parse);
  assert.ok(rows.every(row => row.auditId && row.requestId && row.playerId));
  assert.equal(new Set(rows.map(r => r.auditId)).size, rows.length);
  await flushIntegrationAudit(transact);
  assert.equal(await readFile(join(directory, files[0]), 'utf8'), contents);
});
