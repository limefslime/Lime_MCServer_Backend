import 'dotenv/config';
import {readFile} from 'node:fs/promises';
import pool from '../src/db/pool.js';
try{
 const required=['players','wallets','shop_items','player_mail','delivery_contracts','shop_trade_receipts','integration_audit_outbox'];
 for(const table of required){const {rows:[row]}=await pool.query('SELECT to_regclass($1) AS name',[table]);if(!row?.name)throw new Error(`Existing schema is missing ${table}; apply migrations 001–022 first`);}
 await pool.query(await readFile(new URL('../src/migrations/023_admin_economy.sql',import.meta.url),'utf8'));
 console.log('관리자 경제 스키마 023 적용 완료');
}catch(error){console.error('마이그레이션 실패:',error.code??error.message);process.exitCode=1;}finally{await pool.end();}
