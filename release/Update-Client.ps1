param([string]$ModsPath = "$env:USERPROFILE\curseforge\minecraft\Instances\Lime Colony Economy\mods")
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ModsPath -PathType Container)) { throw "모드 폴더를 찾을 수 없습니다: $ModsPath. -ModsPath로 지정하세요." }
Write-Host 'Minecraft를 종료한 상태에서 적용하세요.'
$backup = Join-Path (Split-Path $ModsPath -Parent) ('economy-mod-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
Get-ChildItem -LiteralPath $ModsPath -Filter 'namanseulfarming*.jar' | Move-Item -Destination $backup
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'namanseulfarming-1.1.1.jar') -Destination $ModsPath
Write-Host "클라이언트 모드 교체 완료. 기존 모드 백업: $backup"
