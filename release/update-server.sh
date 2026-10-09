#!/usr/bin/env bash
set -euo pipefail
bundle=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
backend=/srv/mc/backend
server=/srv/mc/server
backup=/srv/mc/backups/economy-1.1.1-$(date +%Y%m%d-%H%M%S)
[[ -f "$backend/.env" && -f "$server/run.sh" && -d "$server/mods" ]] || { echo '기존 백엔드·서버 경로를 확인하세요.'; exit 1; }
[[ -f "$bundle/Backend.zip" && -f "$bundle/namanseulfarming-1.1.1.jar" ]] || exit 1
command -v pg_dump >/dev/null || { echo 'pg_dump가 필요합니다. PostgreSQL 설치 상태를 확인하세요.'; exit 1; }
if [[ -d /etc/postgresql/16/minecraft ]]; then pg_ctlcluster 16 minecraft start || pg_ctlcluster 16 minecraft status; fi
install -d -m 700 "$backup" "$backup/old-mods"
# Stop only the Minecraft and API processes in these exact working directories.
node --input-type=module <<'JS'
import fs from 'node:fs';
const targets=[];
for(const entry of fs.readdirSync('/proc')){
 if(!/^\d+$/.test(entry)||Number(entry)===process.pid)continue;
 try{const cwd=fs.realpathSync('/proc/'+entry+'/cwd'),args=fs.readFileSync('/proc/'+entry+'/cmdline','utf8').split('\0'),exe=args[0]??'';
  if((cwd==='/srv/mc/server'&&/(^|\/)java$/.test(exe))||(cwd==='/srv/mc/backend'&&/(^|\/)node$/.test(exe)&&args.some(a=>a==='src/app.js'||a==='/srv/mc/backend/src/app.js')))targets.push(Number(entry));
 }catch{}
}
for(const pid of targets)try{process.kill(pid,'SIGTERM');}catch{}
const end=Date.now()+60000;
while(Date.now()<end){const alive=targets.filter(pid=>{try{process.kill(pid,0);return true;}catch{return false;}});if(!alive.length)process.exit(0);await new Promise(r=>setTimeout(r,500));}
console.error('서버 저장 종료를 기다리는 중입니다. 강제 종료하지 않았습니다. 잠시 후 다시 실행하세요.');process.exit(1);
JS
# Preserve secret configuration and original player inventories locally.
tar -czf "$backup/backend.tar.gz" --exclude=node_modules --exclude=logs -C "$backend" .
if [[ -d "$server/world/playerdata" ]]; then tar -czf "$backup/playerdata.tar.gz" -C "$server/world" playerdata; fi
cp "$bundle/backup-db.js" "$backend/.economy-backup-db.js"
(cd "$backend" && node .economy-backup-db.js "$backup/database.sql")
rm "$backend/.economy-backup-db.js"
if [[ -d "$backend/config" ]]; then cp -a "$backend/config" "$backup/config"; fi
# The update zip intentionally contains no .env file.
unzip -oq "$bundle/Backend.zip" -d /srv/mc
if [[ -d "$backup/config" ]]; then cp -a "$backup/config/." "$backend/config/"; fi
(cd "$backend" && npm ci && npm run migrate:admin)
shopt -s nullglob
for jar in "$server"/mods/namanseulfarming*.jar; do mv "$jar" "$backup/old-mods/"; done
cp "$bundle/namanseulfarming-1.1.1.jar" "$server/mods/"
chmod 600 "$backend/.env"
(cd "$backend" && nohup npm start > admin-update-launch.log 2>&1 < /dev/null &)
healthy=0
for ((i=0;i<30;i++)); do if curl --fail --silent --max-time 2 http://127.0.0.1:3000/health >/dev/null; then healthy=1; break; fi; sleep 1; done
((healthy==1)) || { echo "백엔드 시작을 확인하지 못했습니다. $backend/admin-update-launch.log를 확인하세요. 백업: $backup"; exit 1; }
export NFS_BACKEND_BASE_URL=http://127.0.0.1:3000
export NFS_INTEGRATION_API_TOKEN="$(cd "$backend" && node --input-type=module -e "import 'dotenv/config';process.stdout.write(process.env.INTEGRATION_API_TOKEN||'')")"
[[ ${#NFS_INTEGRATION_API_TOKEN} -ge 32 ]] || { echo '기존 연결 토큰 설정을 확인하세요.'; exit 1; }
if [[ -x /srv/mc/java25/bin/java ]]; then export JAVA_HOME=/srv/mc/java25; export PATH="$JAVA_HOME/bin:$PATH"; fi
(cd "$server" && nohup bash run.sh nogui > admin-update-launch.log 2>&1 < /dev/null &)
echo "업데이트 적용 완료. 백업: $backup"
echo "Minecraft 시작 로그: $server/admin-update-launch.log"
echo '클라이언트의 모드도 교체한 뒤 /경제관리를 실행하세요.'
