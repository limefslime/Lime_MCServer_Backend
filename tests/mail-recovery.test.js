import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { claimMail, sendMail, mailErrorCode } from '../src/modules/mail/mail.service.js';
import { claimMailController } from '../src/modules/mail/mail.controller.js';
let db;
const transact = work => db.transaction(client => work(client));
before(async () => {
  db = new PGlite();
  for (const file of ['001_create_wallet_core.sql','002_wallet_schema_upgrade.sql','011_add_player_mail.sql',
    '019_add_integration_rewards.sql','021_add_mail_claim_receipts.sql']) {
    await db.exec((await readFile(new URL('../src/migrations/'+file,import.meta.url),'utf8'))
      .replace('CREATE EXTENSION IF NOT EXISTS pgcrypto;',''));
  }
});
after(async () => {await db.close();});
async function fixture(rewardAmount=50,itemReward={itemId:'aquaculture:atlantic_cod',quantity:3}) {
  const playerId=randomUUID();
  const mail=await sendMail({playerId,title:'Reward',message:'Delivery',rewardAmount,itemReward},db);
  return {playerId,mailId:mail.id,requestId:randomUUID()};
}
async function state(input) {
  return (await db.query(`SELECT is_claimed,
    (SELECT balance FROM wallets WHERE player_id=$2) AS balance,
    (SELECT count(*)::int FROM ledger WHERE player_id=$2) AS ledger,
    (SELECT count(*)::int FROM mail_claim_receipts WHERE mail_id=$1) AS receipts,
    (SELECT count(*)::int FROM integration_audit_outbox WHERE payload->>'mailId'=$1::text) AS audits
    FROM player_mail WHERE id=$1`,[input.mailId,input.playerId])).rows[0];
}
test('money and item mail returns original receipt after lost response, without repaying or reauditing',async()=>{
  const input=await fixture(),first=await claimMail(input.mailId,input,transact);
  assert.equal(first.rewardAmount,50);
  assert.deepEqual(first.rewardInfo.itemReward,{itemId:'aquaculture:atlantic_cod',quantity:3});
  assert.equal(first.mail.playerId,input.playerId); assert.equal(first.mail.isClaimed,true);
  await db.query('UPDATE player_mail SET message=NULL,reward_amount=999 WHERE id=$1',[input.mailId]);
  assert.deepEqual(await claimMail(input.mailId,input,transact),{...first,replayed:true});
  assert.deepEqual(await state(input),{is_claimed:true,balance:50,ledger:1,receipts:1,audits:1});
});
test('overlapping first submissions claim one time',async()=>{
  const input=await fixture();
  const results=await Promise.all(Array.from({length:6},()=>claimMail(input.mailId,input,transact)));
  assert.equal(results.filter(r=>!r.replayed).length,1);
  assert.deepEqual(await state(input),{is_claimed:true,balance:50,ledger:1,receipts:1,audits:1});
});
test('new request ID cannot consume an already claimed mail again',async()=>{
  const input=await fixture(); await claimMail(input.mailId,input,transact);
  const saved=await state(input);
  await assert.rejects(claimMail(input.mailId,{...input,requestId:randomUUID()},transact),e=>e.code===mailErrorCode.MAIL_ALREADY_CLAIMED);
  assert.deepEqual(await state(input),saved);
});
test('same request ID cannot be reused for another mail',async()=>{
  const input=await fixture(); await claimMail(input.mailId,input,transact);
  const second=await sendMail({playerId:input.playerId,title:'Other',message:'Other',rewardAmount:20},db);
  await assert.rejects(claimMail(second.id,input,transact),e=>e.code===mailErrorCode.REQUEST_CONFLICT);
  assert.equal((await db.query('SELECT is_claimed FROM player_mail WHERE id=$1',[second.id])).rows[0].is_claimed,false);
});
test('backend verifies mail owner before consuming mail or crediting wallet',async()=>{
  const input=await fixture(),other=randomUUID();
  await assert.rejects(claimMail(input.mailId,{playerId:other,requestId:input.requestId},transact),e=>e.code===mailErrorCode.MAIL_NOT_FOUND);
  assert.deepEqual(await state(input),{is_claimed:false,balance:null,ledger:0,receipts:0,audits:0});
  assert.equal((await db.query('SELECT * FROM wallets WHERE player_id=$1',[other])).rows.length,0);
});
test('receipt and audit failures roll back claim, balance and ledger, preserving safe retry',async()=>{
  for (const table of ['mail_claim_receipts','integration_audit_outbox']) {
    const input=await fixture();
    const failing=work=>db.transaction(client=>work({query:(sql,args)=>{
      if(sql.startsWith('INSERT INTO '+table))throw new Error('simulated failure');
      return client.query(sql,args);
    }}));
    await assert.rejects(claimMail(input.mailId,input,failing),/simulated failure/);
    assert.deepEqual(await state(input),{is_claimed:false,balance:null,ledger:0,receipts:0,audits:0});
    assert.equal((await claimMail(input.mailId,input,transact)).replayed,false);
  }
});
test('wallet overflow rolls back mail claim and does not consume receipt',async()=>{
  const input=await fixture();
  await db.query('INSERT INTO wallets(player_id,balance) VALUES($1,2147483647)',[input.playerId]);
  await assert.rejects(claimMail(input.mailId,input,transact));
  assert.deepEqual(await state(input),{is_claimed:false,balance:2147483647,ledger:0,receipts:0,audits:0});
});
test('item-only and notification claims are replayable without monetary ledger entries',async()=>{
  for(const itemReward of [{itemId:'farmersdelight:rice',quantity:4},null]) {
    const input=await fixture(0,itemReward),result=await claimMail(input.mailId,input,transact);
    assert.deepEqual(result.rewardInfo.itemReward,itemReward);
    assert.deepEqual(await state(input),{is_claimed:true,balance:0,ledger:0,receipts:1,audits:1});
    assert.equal((await claimMail(input.mailId,input,transact)).replayed,true);
  }
});
test('malformed and out-of-range item tags cannot silently consume a mail',async()=>{
  for(const message of ['[item_reward:broken]','[ITEM_REWARD:broken]',
    '[item_reward:'+encodeURIComponent(JSON.stringify({itemId:'mod:fish',quantity:2147483648}))+']']) {
    const input=await fixture();
    await db.query('UPDATE player_mail SET message=$2 WHERE id=$1',[input.mailId,message]);
    await assert.rejects(claimMail(input.mailId,input,transact),e=>e.code===mailErrorCode.INVALID_ITEM_REWARD);
    assert.equal((await state(input)).is_claimed,false);
  }
});
test('legacy claim shape is retained and cannot later become a protected re-delivery',async()=>{
  const input=await fixture(),result=await claimMail(input.mailId,{},transact);
  assert.equal(Object.hasOwn(result,'requestId'),false);
  assert.equal(Object.hasOwn(result,'replayed'),false);
  await assert.rejects(claimMail(input.mailId,input,transact),e=>e.code===mailErrorCode.MAIL_ALREADY_CLAIMED);
  assert.deepEqual(await state(input),{is_claimed:true,balance:50,ledger:1,receipts:0,audits:1});
});
test('UUID canonicalization and partial identities are validated',async()=>{
  const input=await fixture();
  for(const invalid of [{playerId:input.playerId},{requestId:input.requestId},{playerId:input.playerId,requestId:'bad'}]) {
    await assert.rejects(claimMail(input.mailId,invalid,transact),e=>e.code===mailErrorCode.INVALID_INPUT);
  }
  const first=await claimMail(input.mailId,{playerId:input.playerId.toUpperCase(),requestId:input.requestId.toUpperCase()},transact);
  assert.equal(first.playerId,input.playerId); assert.equal(first.requestId,input.requestId);
  assert.equal((await claimMail(input.mailId,input,transact)).replayed,true);
});
test('known HTTP rejection echoes protected claim identity',async()=>{
  const body={playerId:randomUUID(),requestId:'bad'},mailId=randomUUID();
  let status,result;
  await claimMailController({params:{mailId},body},{status(n){status=n;return this;},json(value){result=value;}});
  assert.equal(status,400);
  assert.deepEqual(result,{...body,mailId,code:'INVALID_INPUT',message:'requestId must be a valid uuid'});
});
