import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, mkdtemp, readdir, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { PGlite } from '@electric-sql/pglite';
import { buyItem, sellItem, shopErrorCode } from '../src/modules/shop/shop.service.js';
import { buyItemController } from '../src/modules/shop/shop.controller.js';
import { flushIntegrationAudit } from '../src/services/integrationAudit.js';

let db;
const transact = work => db.transaction(client => work(client));
before(async () => {
  db = new PGlite();
  const files = ['001_create_wallet_core.sql', '002_wallet_schema_upgrade.sql',
    '003_create_shop_items.sql', '006_add_shop_transactions_and_focus_state.sql',
    '010_add_invest_mvp.sql', '012_add_project_effects.sql', '015_add_events.sql',
    '016_add_shop_item_stock_controls.sql', '019_add_integration_rewards.sql', '020_add_shop_trade_receipts.sql'];
  for (const file of files) {
    const sql = await readFile(new URL('../src/migrations/' + file, import.meta.url), 'utf8');
    await db.exec(sql.replace('CREATE EXTENSION IF NOT EXISTS pgcrypto;', ''));
  }
});
after(async () => { await db.close(); });
async function fixture(balance = 10000) {
  const playerId = randomUUID(), itemId = 'test:' + randomUUID();
  await db.query('INSERT INTO players(id) VALUES($1)', [playerId]);
  await db.query('INSERT INTO wallets(player_id,balance) VALUES($1,$2)', [playerId, balance]);
  await db.query(`INSERT INTO shop_items(item_id,item_name,category,buy_price,sell_price,stock_quantity,replenish_amount)
    VALUES($1,'Test fish','port',100,50,20,0)`, [itemId]);
  return { playerId, itemId, quantity: 2, requestId: randomUUID() };
}
async function state(input) {
  return (await db.query(`SELECT balance,
    (SELECT stock_quantity FROM shop_items WHERE item_id=$2) AS stock,
    (SELECT count(*)::int FROM ledger WHERE player_id=$1) AS ledger,
    (SELECT count(*)::int FROM shop_transactions WHERE player_id=$1) AS trades,
    (SELECT count(*)::int FROM shop_trade_receipts WHERE player_id=$1) AS receipts,
    (SELECT count(*)::int FROM integration_audit_outbox WHERE payload->>'playerId'=$1::text) AS audits
    FROM wallets WHERE player_id=$1`, [input.playerId, input.itemId])).rows[0];
}
test('buy retries preserve original result after price, stock and active status change', async () => {
  const input = await fixture();
  const first = await buyItem(input, transact);
  const committed = await state(input);
  assert.equal(committed.balance, 10000 - first.totalPrice);
  assert.deepEqual([committed.stock, committed.ledger, committed.trades, committed.receipts, committed.audits], [18,1,1,1,1]);
  await db.query('UPDATE shop_items SET buy_price=999,is_active=false,stock_quantity=0 WHERE item_id=$1', [input.itemId]);
  const retries = await Promise.all(Array.from({ length: 6 }, () => buyItem(input, transact)));
  for (const result of retries) assert.deepEqual(result, { ...first, replayed: true });
  assert.deepEqual(await state(input), { ...committed, stock: 0 });
});
test('sell retry credits once and does not add stock twice', async () => {
  const input = await fixture();
  const first = await sellItem(input, transact);
  assert.deepEqual(await sellItem(input, transact), { ...first, replayed: true });
  const saved = await state(input);
  assert.equal(saved.balance, 10000 + first.totalPrice);
  assert.deepEqual([saved.stock,saved.ledger,saved.trades,saved.receipts,saved.audits], [22,1,1,1,1]);
});
test('request reuse with changed direction, item or quantity is a conflict with no side effects', async () => {
  const input = await fixture(); await buyItem(input, transact);
  const committed = await state(input);
  for (const [operation, changed] of [[sellItem, input], [buyItem, {...input,quantity:3}], [buyItem, {...input,itemId:'missing:item'}]]) {
    await assert.rejects(operation(changed, transact), error => error.code === shopErrorCode.REQUEST_CONFLICT);
  }
  assert.deepEqual(await state(input), committed);
});
test('outbox failure rolls back both buy and sell, permitting safe retries', async () => {
  for (const operation of [buyItem, sellItem]) {
    const input = await fixture(), initial = await state(input);
    const failing = work => db.transaction(client => work({query: (sql,args) => {
      if (sql.startsWith('INSERT INTO integration_audit_outbox')) throw new Error('disk bridge unavailable');
      return client.query(sql,args);
    }}));
    await assert.rejects(operation(input, failing), /disk bridge unavailable/);
    assert.deepEqual(await state(input), initial);
    assert.equal((await operation(input, transact)).replayed, false);
  }
});
test('insufficient balance does not consume request ID or decrease stock', async () => {
  const input = await fixture(0), initial = await state(input);
  await assert.rejects(buyItem(input, transact), error => error.code === shopErrorCode.INSUFFICIENT_BALANCE);
  assert.deepEqual(await state(input), initial);
  await db.query('UPDATE wallets SET balance=10000 WHERE player_id=$1', [input.playerId]);
  assert.equal((await buyItem(input, transact)).replayed, false);
});
test('request IDs are UUIDs, canonicalized, and scoped to the player', async () => {
  const input = await fixture();
  for (const requestId of [null, '', 'invalid']) {
    await assert.rejects(buyItem({...input,requestId}, transact), error => error.code === shopErrorCode.INVALID_INPUT);
  }
  const first = await buyItem({...input,requestId:input.requestId.toUpperCase()}, transact);
  assert.equal(first.requestId, input.requestId);
  assert.equal((await buyItem(input, transact)).replayed, true);
  const other = await fixture();
  assert.equal((await buyItem({...other,requestId:input.requestId}, transact)).replayed, false);
});
test('legacy requests retain response shape and audit each independent trade', async () => {
  const input = await fixture(); delete input.requestId;
  const first = await buyItem(input, transact);
  assert.equal(Object.hasOwn(first,'replayed'), false);
  await buyItem(input, transact);
  const saved = await state(input);
  assert.deepEqual([saved.stock,saved.trades,saved.receipts,saved.audits], [16,2,0,2]);
});
test('HTTP rejects invalid request UUID with 400 before touching the database', async () => {
  let status, body;
  await buyItemController({body:{playerId:randomUUID(),itemId:'test:item',quantity:1,requestId:'bad'}}, {
    status(value) {status=value;return this;}, json(value) {body=value;}
  });
  assert.equal(status,400); assert.match(body.message,/requestId/);
});
test('committed shop audit reaches durable local JSONL with matching receipt ID', async () => {
  const input = await fixture(); await buyItem(input, transact);
  const directory = await mkdtemp(join(tmpdir(),'shop-audit-'));
  const previous = process.env.AUDIT_LOG_DIR;
  process.env.AUDIT_LOG_DIR = directory;
  try {
    await flushIntegrationAudit(transact);
    const files = await readdir(directory);
    const lines = (await Promise.all(files.map(file => readFile(join(directory,file),'utf8')))).join('')
      .trim().split('\n').map(line => JSON.parse(line));
    const records = lines.filter(line => line.requestId === input.requestId);
    assert.equal(records.length,1); assert.equal(records[0].event,'shop_trade');
    assert.equal(records[0].transactionType,'buy'); assert.ok(records[0].auditId);
    await flushIntegrationAudit(transact);
    assert.equal((await readFile(join(directory,files[0]),'utf8')).trim().split('\n').length,lines.length);
  } finally {
    if (previous === undefined) delete process.env.AUDIT_LOG_DIR; else process.env.AUDIT_LOG_DIR = previous;
    await rm(directory,{recursive:true});
  }
});

test('overlapping first submissions produce one receipt, ledger, stock update and audit', async () => {
  const input = await fixture();
  const results = await Promise.all(Array.from({length:8}, () => buyItem(input, transact)));
  assert.equal(results.filter(result => !result.replayed).length,1);
  const saved = await state(input);
  assert.deepEqual([saved.stock,saved.ledger,saved.trades,saved.receipts,saved.audits],[18,1,1,1,1]);
});
