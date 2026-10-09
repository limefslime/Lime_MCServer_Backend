import 'dotenv/config';
import {spawnSync} from 'node:child_process';
import {chmodSync} from 'node:fs';
const output=process.argv[2];if(!output)throw new Error('Backup path required');
const env={...process.env,PGHOST:process.env.DB_HOST||'127.0.0.1',PGPORT:process.env.DB_PORT||'5432',PGDATABASE:process.env.DB_NAME,PGUSER:process.env.DB_USER,PGPASSWORD:process.env.DB_PASSWORD};
if(process.env.DATABASE_URL){const u=new URL(process.env.DATABASE_URL);Object.assign(env,{PGHOST:u.hostname,PGPORT:u.port||'5432',PGDATABASE:decodeURIComponent(u.pathname.slice(1)),PGUSER:decodeURIComponent(u.username),PGPASSWORD:decodeURIComponent(u.password)});}
const result=spawnSync('pg_dump',['--no-owner','--no-privileges','--file',output],{env,stdio:['ignore','ignore','inherit']});if(result.error||result.status!==0){console.error('Database backup failed');process.exit(1);}chmodSync(output,0o600);console.log('데이터베이스 백업 완료');
