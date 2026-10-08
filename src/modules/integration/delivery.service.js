import { readFile } from 'node:fs/promises';
import { withTransaction } from '../../db/pool.js';
import { ensurePlayerExists, ensureWalletExists, insertLedgerEntry } from '../wallet/wallet.repository.js';
import { RewardError } from './reward.core.js';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function identity(raw) {
  if (typeof raw !== 'string' || !UUID.test(raw)) throw new RewardError('invalid player/request UUID');
  return raw.toLowerCase();
}
export async function deliveryPolicies() {
  const path = process.env.DELIVERY_POLICY_PATH || new URL('../../../config/delivery-contracts.json', import.meta.url);
  const policies = JSON.parse(await readFile(path, 'utf8'));
  if (!Array.isArray(policies) || policies.length > 30) throw new RewardError('invalid delivery policy', 503);
  const seen = new Set();
  for (const p of policies) {
    if (!p || typeof p.id !== 'string' || !/^[a-z0-9_-]{1,64}$/.test(p.id) || seen.has(p.id)
      || typeof p.title !== 'string' || !p.title.trim() || p.title.length > 80
      || typeof p.itemId !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(p.itemId)
      || !Number.isSafeInteger(p.quantity) || p.quantity < 1 || p.quantity > 1000
      || !Number.isSafeInteger(p.reward) || p.reward < 1 || p.reward > 2147483647
      || !Number.isSafeInteger(p.cooldownSeconds) || p.cooldownSeconds < 0 || p.cooldownSeconds > 31536000) {
      throw new RewardError('invalid delivery policy', 503);
    }
    seen.add(p.id);
  }
  return policies;
}
async function lock(client, playerId) {
  await ensurePlayerExists(playerId, client); await ensureWalletExists(playerId, client);
  await client.query('SELECT player_id FROM wallets WHERE player_id=$1 FOR UPDATE', [playerId]);
}
function view(row) {
  return { ...row.definition, templateId: row.template_id, id: row.id, delivered: row.delivered,
    status: row.status, acceptedAt: row.accepted_at, completedAt: row.completed_at };
}
function nextTime(row) {
  return new Date(new Date(row.completed_at).getTime() + row.definition.cooldownSeconds * 1000);
}
export async function listDeliveries(raw, transaction = withTransaction) {
  const playerId = identity(raw); const policies = await deliveryPolicies();
  return transaction(async client => {
    await lock(client, playerId);
    const { rows } = await client.query('SELECT * FROM delivery_contracts WHERE player_id=$1 ORDER BY accepted_at DESC', [playerId]);
    const { rows: [clock] } = await client.query('SELECT clock_timestamp() AS now');
    const active = rows.filter(r => r.status === 'active');
    const available = policies.map(p => {
      const previous = rows.find(r => r.template_id === p.id);
      const eligibleAt = previous?.status === 'completed' ? nextTime(previous) : null;
      const reason = active.some(r => r.template_id === p.id) ? 'already_active'
        : active.length >= 3 ? 'active_limit' : eligibleAt && eligibleAt > clock.now ? 'cooldown' : null;
      return { ...p, canAccept: !reason, reason, nextEligibleAt: eligibleAt };
    });
    return { available, active: active.map(view), completed: rows.filter(r => r.status === 'completed').slice(0,30).map(view) };
  });
}
export async function getDelivery(raw, id, transaction = withTransaction) {
  const playerId = identity(raw); id = identity(id);
  return transaction(async client => {
    const { rows } = await client.query('SELECT * FROM delivery_contracts WHERE player_id=$1 AND id=$2', [playerId,id]);
    if (!rows.length) throw new RewardError('delivery not found', 404);
    return view(rows[0]);
  });
}
async function previous(client, input) {
  const { rows } = await client.query('SELECT * FROM delivery_receipts WHERE player_id=$1 AND request_id=$2', [input.playerId,input.requestId]);
  if (!rows.length) return null;
  const r = rows[0];
  if (r.operation !== input.operation || r.target !== input.target || r.item_id !== input.itemId || r.quantity !== input.quantity) {
    throw new RewardError('requestId reused for another delivery operation',409);
  }
  return { ...r.result, replayed: true };
}
async function save(client, input, result) {
  await client.query('INSERT INTO delivery_receipts(player_id,request_id,operation,target,item_id,quantity,result) VALUES($1,$2,$3,$4,$5,$6,$7)',
    [input.playerId,input.requestId,input.operation,input.target,input.itemId,input.quantity,result]);
  await client.query('INSERT INTO integration_audit_outbox(payload) VALUES($1)', [{event:'delivery_'+input.operation,...result}]);
  return result;
}
export async function acceptDelivery(raw, transaction = withTransaction) {
  const input = {playerId:identity(raw?.playerId),requestId:identity(raw?.requestId),operation:'accept',target:raw?.templateId,itemId:'',quantity:0};
  if (typeof input.target !== 'string' || !/^[a-z0-9_-]{1,64}$/.test(input.target)) throw new RewardError('invalid templateId');
  const policies = await deliveryPolicies();
  return transaction(async client => {
    await lock(client,input.playerId);
    const receipt = await previous(client,input); if (receipt) return receipt;
    const base = {...input, accepted:false};
    const reject = (code,message) => save(client,input,{...base,code,message});
    const policy = policies.find(p => p.id === input.target);
    if (!policy) return reject('NOT_FOUND','의뢰를 찾을 수 없습니다.');
    const {rows} = await client.query('SELECT * FROM delivery_contracts WHERE player_id=$1 ORDER BY accepted_at DESC', [input.playerId]);
    if (rows.some(r => r.template_id === input.target && r.status === 'active')) return reject('ACTIVE','이미 수락한 의뢰입니다.');
    if (rows.filter(r => r.status === 'active').length >= 3) return reject('LIMIT','수락 가능한 의뢰는 최대 3개입니다.');
    const last = rows.find(r => r.template_id === input.target && r.status === 'completed');
    const {rows:[clock]} = await client.query('SELECT clock_timestamp() AS now');
    if (last && nextTime(last) > clock.now) return reject('COOLDOWN','같은 의뢰는 완료 후 대기 시간이 필요합니다.');
    const {rows:[row]} = await client.query('INSERT INTO delivery_contracts(player_id,template_id,definition) VALUES($1,$2,$3) RETURNING *',
      [input.playerId,input.target,policy]);
    return save(client,input,{...base,accepted:true,contract:view(row)});
  });
}
export async function submitDelivery(raw, transaction = withTransaction) {
  const input = {playerId:identity(raw?.playerId),requestId:identity(raw?.requestId),operation:'submit',target:identity(raw?.contractId),itemId:raw?.itemId,quantity:raw?.quantity};
  if (typeof input.itemId !== 'string' || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(input.itemId)
    || !Number.isSafeInteger(input.quantity) || input.quantity < 1 || input.quantity > 1000) throw new RewardError('invalid delivery item/quantity');
  return transaction(async client => {
    await lock(client,input.playerId);
    const receipt = await previous(client,input); if (receipt) return receipt;
    const base = {...input,contractId:input.target,accepted:false};
    const reject = (code,message) => save(client,input,{...base,code,message});
    const {rows:[row]} = await client.query('SELECT * FROM delivery_contracts WHERE player_id=$1 AND id=$2 FOR UPDATE',[input.playerId,input.target]);
    if (!row) return reject('NOT_FOUND','수락한 의뢰를 찾을 수 없습니다.');
    if (row.status !== 'active') return reject('COMPLETED','이미 완료한 의뢰입니다.');
    if (row.definition.itemId !== input.itemId || input.quantity > row.definition.quantity-row.delivered) return reject('MISMATCH','품목이나 남은 수량이 맞지 않습니다.');
    const delivered = row.delivered+input.quantity;
    const complete = delivered === row.definition.quantity;
    let reward = 0, balance = null;
    if (complete) {
      reward = row.definition.reward;
      const {rows:[wallet]} = await client.query('UPDATE wallets SET balance=balance+$2,updated_at=NOW() WHERE player_id=$1 AND balance::bigint+$2<=2147483647 RETURNING balance',[input.playerId,reward]);
      if (!wallet) return reject('BALANCE_LIMIT','지갑 잔액 한도에 도달했습니다.');
      balance = Number(wallet.balance);
      await insertLedgerEntry({playerId:input.playerId,type:'add',amount:reward,reason:'quest_reward'},client);
    }
    const {rows:[updated]} = await client.query("UPDATE delivery_contracts SET delivered=$2,status=$3,completed_at=CASE WHEN $3='completed' THEN NOW() ELSE NULL END WHERE id=$1 RETURNING *",[row.id,delivered,complete?'completed':'active']);
    return save(client,input,{...base,accepted:true,contract:view(updated),reward,balance});
  });
}
