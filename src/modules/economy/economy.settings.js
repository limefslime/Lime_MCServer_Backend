import pool from '../../db/pool.js';
export const DEFAULT_RULES={maintenance:false,buyFeeRate:0.02,sellFeeRate:0.03,minFee:0,maxSellQuantity:1000,flipCooldownSeconds:10,deliveryActiveLimit:3,projectRewardRate:0.2,walletLimit:2147483647};
let rules={...DEFAULT_RULES};
export const economyRules=()=>rules;
export async function settingValue(key,fallback,db=pool){
 const {rows:[table]}=await db.query("SELECT to_regclass('economy_settings') AS name");
 if(!table?.name)return fallback;
 const {rows}=await db.query('SELECT value FROM economy_settings WHERE key=$1',[key]);return rows[0]?.value??fallback;
}
export async function loadEconomySettings(db=pool){rules={...DEFAULT_RULES,...await settingValue('rules',{},db)};return rules;}
export async function assertEconomyAvailable(db,playerId){
 if(rules.maintenance)throw Object.assign(new Error('경제 시스템 점검 중입니다.'),{status:503});
 const {rows:[table]}=await db.query("SELECT to_regclass('economy_player_controls') AS name");
 if(!table?.name)return;
 const {rows}=await db.query('SELECT frozen FROM economy_player_controls WHERE player_id=$1',[playerId]);
 if(rows[0]?.frozen)throw Object.assign(new Error('경제 이용이 정지된 계정입니다.'),{status:403});
}
