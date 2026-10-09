$ErrorActionPreference = 'Stop'
$bundle = $PSScriptRoot
& docker start mc-ubuntu | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'mc-ubuntu 컨테이너를 시작하지 못했습니다.' }
& docker exec mc-ubuntu mkdir -p /srv/mc/economy-update-1.1.1
& docker cp "$bundle\." 'mc-ubuntu:/srv/mc/economy-update-1.1.1'
if ($LASTEXITCODE -ne 0) { throw '업데이트 파일 복사에 실패했습니다.' }
& docker exec mc-ubuntu bash /srv/mc/economy-update-1.1.1/update-server.sh
if ($LASTEXITCODE -ne 0) { throw '업데이트를 완료하지 못했습니다. 위에 표시된 로그와 백업 경로를 확인하세요.' }
