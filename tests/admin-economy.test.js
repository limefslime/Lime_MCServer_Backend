import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {PGlite} from '@electric-sql/pglite';
import {adminExecute,adminRead} from '../src/modules/admin/admin.operations.js';
import {claimMail} from '../src/modules/mail/mail.service.js';
const db=new PGlite();await db.waitReady;const tx=work=>db.transaction(work),actor=randomUUID();
const run=(action,fields={},requestId=randomUUID())=>adminExecute(actor,'Admin',{action,fields:{reason:'test',...fields},requestId},tx);
before(async()=>{for(const file of (await readdir(new URL('../src/migrations/',import.meta.url))).filter(x=>x.endsWith('.sql')).sort())await db.exec((await readFile(new URL('../src/migrations/'+file,import.meta.url),'utf8')).replace('CREATE EXTENSION IF NOT EXISTS pgcrypto;',''));});
after(()=>db.close());
test('all migrations and admin catalog reads work',async()=>{for(const action of ['players','shop_list','rules_get','deliveries','rewards','projects','events','regions','permissions','logs','health'])assert.ok(await adminRead(actor,action,{},db));});
test('concurrent retry pays exactly once and rejects UUID reuse',async()=>{const playerId=randomUUID(),requestId=randomUUID(),fields={playerId,amount:123};const results=await Promise.all(Array.from({length:5},()=>run('wallet_add',fields,requestId)));assert.equal(results[0].balance,123);assert.equal((await db.query('SELECT balance FROM wallets WHERE player_id=$1',[playerId])).rows[0].balance,123);assert.equal((await db.query('SELECT COUNT(*) n FROM ledger WHERE player_id=$1',[playerId])).rows[0].n,1);await assert.rejects(run('wallet_add',{...fields,amount:124},requestId),/같은 요청 번호/);await assert.rejects(run('wallet_set',{playerId,amount:1000,expectedBalance:0}),/잔액이 변경/);});
test('permission denial leaves wallet untouched',async()=>{const operator=randomUUID(),playerId=randomUUID();await db.query('INSERT INTO admin_permissions VALUES($1,$2)',[operator,JSON.stringify(['logs'])]);await assert.rejects(adminExecute(operator,'Restricted',{action:'wallet_add',requestId:randomUUID(),fields:{playerId,amount:100,reason:'test'}},tx),/관리 권한/);assert.equal((await db.query('SELECT * FROM wallets WHERE player_id=$1',[playerId])).rows.length,0);});
test('audit failure rolls back wallet, ledger and receipt',async()=>{const playerId=randomUUID(),requestId=randomUUID();const broken=work=>db.transaction(client=>work({query:(sql,args)=>{if(sql.startsWith('INSERT INTO integration_audit_outbox'))throw new Error('disk simulation');return client.query(sql,args);}}));await assert.rejects(adminExecute(actor,'Admin',{action:'wallet_add',requestId,fields:{playerId,amount:77,reason:'test'}},broken),/simulation/);assert.equal((await db.query('SELECT * FROM wallets WHERE player_id=$1',[playerId])).rows.length,0);assert.equal((await run('wallet_add',{playerId,amount:77},requestId)).balance,77);});
test('shop price, stock and policy validation',async()=>{const fields={itemId:'minecraft:diamond',itemName:'Diamond',category:'misc',buyPrice:30,sellPrice:10,stockQuantity:20,replenishAmount:3,replenishIntervalSeconds:300,isActive:'true'};await assert.rejects(run('shop_save',{...fields,buyPrice:0}),/정수 입력/);await assert.rejects(run('shop_save',{...fields,sellPrice:31}),/판매 가격/);assert.equal((await run('shop_save',fields)).stock_quantity,20);await run('delivery_save',{id:'test',title:'test',itemId:'minecraft:wheat',quantity:10,reward:25,cooldownSeconds:0});assert.equal((await adminRead(actor,'deliveries',{},db)).rows.find(x=>x.id==='test').reward,25);});
test('recovery mail preserves component payload and cancellation blocks claim',async()=>{const playerId=randomUUID(),stack='{id:"minecraft:diamond",count:1,components:{"minecraft:custom_name":\'"Test"\'}}';const mail=await run('mail_send',{playerId,title:'Recovery',rewardAmount:5,itemId:'minecraft:diamond',quantity:2,stack});const input={playerId,mailId:mail.id,requestId:randomUUID()};const result=await claimMail(input.mailId,input,tx);assert.equal(result.rewardInfo.itemReward.stack,stack);assert.equal(result.rewardInfo.itemReward.quantity,2);assert.equal((await claimMail(input.mailId,input,tx)).replayed,true);await assert.rejects(run('mail_cancel',{id:mail.id}),/이미 수령/);const other=await run('mail_send',{playerId,title:'Cancelled',rewardAmount:100});await run('mail_cancel',{id:other.id});await assert.rejects(claimMail(other.id,{playerId,requestId:randomUUID()},tx),/cancelled/);});
test('project refund is atomic and cannot be repeated with another request',async()=>{const playerId=randomUUID();await run('wallet_add',{playerId,amount:10});const project=await run('project_save',{name:'Test project',description:'',targetAmount:1000,region:'agri'});await db.query('INSERT INTO invest_contributions(project_id,player_id,amount) VALUES($1,$2,100)',[project.id,playerId]);await db.query('UPDATE invest_projects SET current_amount=100 WHERE id=$1',[project.id]);const id=randomUUID();await run('project_refund',{id:project.id},id);await run('project_refund',{id:project.id},id);assert.equal((await db.query('SELECT balance FROM wallets WHERE player_id=$1',[playerId])).rows[0].balance,110);await assert.rejects(run('project_refund',{id:project.id}),/진행 중인/);});
test('investment retries debit once, cap target and deliver completion reward once',async()=>{
 const {investToProject}=await import('../src/modules/invest/invest.service.js');
 const playerId=randomUUID();await run('wallet_add',{playerId,amount:500});
 const project=await run('project_save',{name:'Funded project',description:'',targetAmount:100,region:'agri'});
 const input={playerId,requestId:randomUUID(),amount:100};
 const results=await Promise.all([investToProject(project.id,input,tx),investToProject(project.id,input,tx)]);
 assert.equal(results[1].replayed,true);
 assert.equal((await db.query('SELECT balance FROM wallets WHERE player_id=$1',[playerId])).rows[0].balance,400);
 assert.equal((await db.query('SELECT reward_amount FROM project_reward_logs WHERE project_id=$1',[project.id])).rows[0].reward_amount,20);
 assert.equal((await db.query('SELECT COUNT(*) n FROM project_reward_logs WHERE project_id=$1',[project.id])).rows[0].n,1);
 await assert.rejects(investToProject(project.id,{...input,amount:101},tx),/reused/);
 await assert.rejects(investToProject(project.id,{...input,requestId:randomUUID()},tx),/not active/);
});
test('settings and freeze affect new trades while successful receipts still replay',async()=>{
 const {DEFAULT_RULES,loadEconomySettings}=await import('../src/modules/economy/economy.settings.js');
 const {buyItem}=await import('../src/modules/shop/shop.service.js');const playerId=randomUUID();await run('wallet_add',{playerId,amount:100});
 await run('rules_save',{...DEFAULT_RULES,buyFeeRate:.1});await loadEconomySettings(db);
 const input={playerId,itemId:'minecraft:diamond',quantity:1,requestId:randomUUID()};const first=await buyItem(input,tx);assert.equal(first.totalPrice,33);
 await run('player_freeze',{playerId,frozen:'true'});assert.equal((await buyItem(input,tx)).replayed,true);await assert.rejects(buyItem({...input,requestId:randomUUID()},tx),/이용이 정지/);
 await run('player_freeze',{playerId,frozen:'false'});await run('rules_save',{...DEFAULT_RULES,maintenance:'true'});await loadEconomySettings(db);await assert.rejects(buyItem({...input,requestId:randomUUID()},tx),/점검/);
 await run('rules_save',DEFAULT_RULES);await loadEconomySettings(db);
});
test('purchase compensation refunds the charged total exactly once and includes original source',async()=>{
 const {buyItem}=await import('../src/modules/shop/shop.service.js');const playerId=randomUUID();await run('wallet_add',{playerId,amount:100});const input={playerId,itemId:'minecraft:diamond',quantity:1,requestId:randomUUID()};const trade=await buyItem(input,tx);const sourceId=playerId+'/'+input.requestId,id=randomUUID();
 const restored=await run('trade_refund',{sourceId},id);assert.equal(restored.balance,100);assert.equal(restored.sourceId,sourceId);assert.equal((await run('trade_refund',{sourceId},id)).replayed,true);await assert.rejects(run('trade_refund',{sourceId}),/이미 환불/);assert.ok(trade.totalPrice>0);
});

test('blank policy IDs generate once and replay their original ID',async()=>{
 const requestId=randomUUID();const first=await run('reward_save',{id:'',amount:17,cooldownSeconds:0},requestId);
 assert.match(first.id,/^[0-9A-F]{16}$/);assert.equal((await run('reward_save',{id:'',amount:17,cooldownSeconds:0},requestId)).id,first.id);
 const other=await run('reward_save',{amount:18,cooldownSeconds:0});assert.notEqual(other.id,first.id);
 assert.equal((await adminRead(actor,'rewards',{},db)).rows.filter(x=>x.id===first.id).length,1);
 const delivery=await run('delivery_save',{id:'',title:'자동 의뢰',itemId:'minecraft:wheat',quantity:2,reward:4,cooldownSeconds:0});assert.match(delivery.id,/^delivery_[0-9a-f]{32}$/);
});
test('ID lookup pages, literal search and normalized player rows',async()=>{
 for(let i=0;i<13;i++){const id=randomUUID();await db.query('INSERT INTO players(id,username) VALUES($1,$2)',[id,'Lookup_'+String(i).padStart(2,'0')]);}
 const params={operation:'wallet_add',field:'playerId',search:'Lookup_',pageSize:5};
 const first=await adminRead(actor,'lookup',params,db),last=await adminRead(actor,'lookup',{...params,page:2},db);
 assert.equal(first.total,13);assert.equal(first.rows.length,5);assert.equal(first.hasMore,true);assert.equal(last.rows.length,3);assert.equal(last.hasMore,false);
 assert.ok(first.rows.every(r=>r.occurredAt&&r.playerName&&r.value===r.playerId));
 assert.equal((await adminRead(actor,'lookup',{...params,search:'Lookup_%'},db)).total,0);
 await assert.rejects(adminRead(actor,'lookup',{...params,pageSize:100},db),/페이지/);
});
test('lookup authorizes original operation and cannot switch arbitrary fields or tables',async()=>{
 const operator=randomUUID();await db.query('INSERT INTO admin_permissions VALUES($1,$2)',[operator,JSON.stringify(['logs'])]);
 await assert.rejects(adminRead(operator,'lookup',{operation:'wallet_add',field:'playerId'},db),/관리 권한/);
 await assert.rejects(adminRead(actor,'lookup',{operation:'wallet_add',field:'amount',kind:'purchases'},db),/검색할 수 없는/);
 const kinds=[['reward_save','id'],['delivery_save','id'],['mail_cancel','id'],['delivery_cancel','id'],['project_refund','id'],['event_end','id'],['trade_refund','sourceId'],['permission_set','actorId']];
 for(const [operation,field] of kinds){const data=await adminRead(actor,'lookup',{operation,field},db);assert.ok(Array.isArray(data.rows));assert.ok(Number.isInteger(data.total));}
});
test('refund picker excludes already compensated receipts and supplies exact identity',async()=>{
 const {buyItem}=await import('../src/modules/shop/shop.service.js');const playerId=randomUUID();await run('wallet_add',{playerId,amount:100});const requestId=randomUUID();await buyItem({playerId,itemId:'minecraft:diamond',quantity:1,requestId},tx);
 const params={operation:'trade_refund',field:'sourceId',search:requestId};const data=await adminRead(actor,'lookup',params,db);
 assert.equal(data.rows[0].value,playerId+'/'+requestId);assert.ok(data.rows[0].occurredAt);assert.equal(data.rows[0].playerId,playerId);
 await run('trade_refund',{sourceId:data.rows[0].value});assert.equal((await adminRead(actor,'lookup',params,db)).total,0);
});
test('Korean permission labels retain their canonical authorization scopes',async()=>{
 const operator=randomUUID();await run('permission_set',{actorId:operator,permissions:'지갑, 상점'});
 const permissions=(await db.query('SELECT permissions FROM admin_permissions WHERE actor_id=$1',[operator])).rows[0].permissions;
 assert.deepEqual(permissions,['wallet','shop']);assert.ok(await adminRead(operator,'players',{},db));await assert.rejects(adminRead(operator,'rewards',{},db),/관리 권한/);
});
