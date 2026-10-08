import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {PGlite} from '@electric-sql/pglite';
import {acceptDelivery,submitDelivery,listDeliveries,getDelivery} from '../src/modules/integration/delivery.service.js';
const db=new PGlite();
await db.waitReady;
let dir;
const tx=work=>db.transaction(client=>work(client));
const accept=(playerId=randomUUID(),templateId='wheat')=>({playerId,templateId,requestId:randomUUID()});
const submit=(playerId,contractId,quantity,itemId='minecraft:wheat')=>({playerId,contractId,quantity,itemId,requestId:randomUUID()});
before(async()=>{
 for(const file of ['001_create_wallet_core.sql','019_add_integration_rewards.sql','022_add_delivery_contracts.sql'])
  await db.exec((await readFile(new URL('../src/migrations/'+file,import.meta.url),'utf8')).replace('CREATE EXTENSION IF NOT EXISTS pgcrypto;',''));
 dir=await mkdtemp(join(tmpdir(),'nfs-delivery-'));
 process.env.DELIVERY_POLICY_PATH=join(dir,'policies.json');
 await writeFile(process.env.DELIVERY_POLICY_PATH,await readFile(new URL('../config/delivery-contracts.json',import.meta.url)));
});
after(async()=>{await db.close();await rm(dir,{recursive:true});delete process.env.DELIVERY_POLICY_PATH;});
test('accept survives queries and duplicate calls; UUID is bound to the original operation',async()=>{
 const input=accept();const first=await acceptDelivery(input,tx);
 assert.equal(first.accepted,true);
 assert.equal((await acceptDelivery(input,tx)).contract.id,first.contract.id);
 assert.equal((await listDeliveries(input.playerId,tx)).active.length,1);
 assert.equal((await acceptDelivery({...input,requestId:randomUUID()},tx)).code,'ACTIVE');
 await assert.rejects(acceptDelivery({...input,templateId:'cod'},tx),/reused/);
 await assert.rejects(getDelivery(randomUUID(),first.contract.id,tx),/not found/);
});
test('partial delivery then concurrent final retries consume progress and pay exactly once',async()=>{
 const input=accept();const {contract}=await acceptDelivery(input,tx);
 const partial=await submitDelivery(submit(input.playerId,contract.id,20),tx);
 assert.equal(partial.contract.delivered,20);assert.equal(partial.reward,0);
 const request=submit(input.playerId,contract.id,44);
 const results=await Promise.all(Array.from({length:5},()=>submitDelivery(request,tx)));
 assert.equal(results.filter(r=>!r.replayed).length,1);
 assert.equal(results[0].reward,70);assert.equal(results[0].contract.status,'completed');
 assert.equal((await db.query('SELECT balance FROM wallets WHERE player_id=$1',[input.playerId])).rows[0].balance,70);
 assert.equal(Number((await db.query('SELECT count(*) n FROM ledger WHERE player_id=$1',[input.playerId])).rows[0].n),1);
 assert.equal((await listDeliveries(input.playerId,tx)).completed.length,1);
 assert.equal((await acceptDelivery(accept(input.playerId),tx)).code,'COOLDOWN');
 assert.equal((await submitDelivery(submit(input.playerId,contract.id,1),tx)).code,'COMPLETED');
 await assert.rejects(submitDelivery({...request,quantity:43},tx),/reused/);
});
test('wrong owner, item, excess quantity and wallet overflow reject without changing progress',async()=>{
 const input=accept();const {contract}=await acceptDelivery(input,tx);
 assert.equal((await submitDelivery(submit(randomUUID(),contract.id,64),tx)).code,'NOT_FOUND');
 assert.equal((await submitDelivery(submit(input.playerId,contract.id,64,'minecraft:diamond'),tx)).code,'MISMATCH');
 assert.equal((await submitDelivery(submit(input.playerId,contract.id,65),tx)).code,'MISMATCH');
 await db.query('UPDATE wallets SET balance=2147483647 WHERE player_id=$1',[input.playerId]);
 const r=submit(input.playerId,contract.id,64);
 assert.equal((await submitDelivery(r,tx)).code,'BALANCE_LIMIT');
 assert.equal((await getDelivery(input.playerId,contract.id,tx)).delivered,0);
 assert.equal((await submitDelivery(r,tx)).replayed,true);
});
test('audit failure rolls back progress, reward, receipt and ledger before retry',async()=>{
 const input=accept();const {contract}=await acceptDelivery(input,tx);const request=submit(input.playerId,contract.id,64);
 const fail=work=>db.transaction(client=>work({query:(sql,args)=>{if(sql.startsWith('INSERT INTO integration_audit_outbox'))throw Error('disk failure');return client.query(sql,args);}}));
 await assert.rejects(submitDelivery(request,fail),/disk failure/);
 assert.equal((await getDelivery(input.playerId,contract.id,tx)).delivered,0);
 assert.equal((await db.query('SELECT balance FROM wallets WHERE player_id=$1',[input.playerId])).rows[0].balance,0);
 assert.equal((await submitDelivery(request,tx)).reward,70);
});
test('accepted requirement and reward stay fixed after configuration changes',async()=>{
 const input=accept();const {contract}=await acceptDelivery(input,tx);
 const original=await readFile(process.env.DELIVERY_POLICY_PATH,'utf8');
 try{
  const policies=JSON.parse(original);policies[0].quantity=1;policies[0].reward=999;
  await writeFile(process.env.DELIVERY_POLICY_PATH,JSON.stringify(policies));
  assert.equal((await getDelivery(input.playerId,contract.id,tx)).quantity,64);
  assert.equal((await submitDelivery(submit(input.playerId,contract.id,64),tx)).reward,70);
 }finally{await writeFile(process.env.DELIVERY_POLICY_PATH,original);}
});
