# Lime Colony S2 경제 연결 — 0.1.0

첫 단계: EconomyCraft 제거, Farmer's Delight/Aquaculture/Create 추가,
FTB 퀘스트 보상을 우리 PostgreSQL 지갑에 연결한다. Trade Post 코인은 독립 유지한다.

## 구성

| 모드 | 고정 파일 |
|---|---|
| Farmer's Delight | 1.3.4 / CurseForge 8765184 |
| Aquaculture | 2.7.19 / CurseForge 7761121 |
| Create | 6.0.10 / CurseForge 7963363 |

세 파일의 실제 JAR 메타데이터와 SHA256은 additions.lock.json을 통해 확인했다.
Create JAR 안에 Registrate, Flywheel, Ponder가 포함된다. 기존 S2의 Sodium
0.8.12, Lithium 0.15.4는 Create의 선언된 최소 버전보다 높다. 전체 팩 호환성은
실행 시험이 필요하다. Colony Logistics는 이번 승인 대상 세 모드에 포함되지 않아 추가하지 않았다.

## 빌드와 설치

1. 원본 S2 1.4 ZIP을 별도 폴더에 푼다.
2. `python scripts/build-s2-overlay.py /path/to/S2-1.4 dist/Lime_Colony_S2_0.1.0.zip` 실행.
3. 새 ZIP을 CurseForge에 가져온다. EconomyCraft와 기존 운영 데이터는 포함되지 않는다.
4. Java 21에서 우리 모드 프로젝트의 `gradlew build` 실행 후 JAR을 클라이언트와 서버에 설치한다.
   생성 팩은 우리 모드 JAR을 자동 다운로드하지 않는다. JAR 설치 전 퀘스트 보상을 수령하지 않는다.
5. 기존 DB 마이그레이션 001~016이 적용된 DB에 `019_add_integration_rewards.sql` → `020_add_shop_trade_receipts.sql` 순서로 적용한 뒤 백엔드를 업데이트한다. 기존 DB는 SQL 파일을 수동 적용해야 한다.
   017/018은 별도 작업 브랜치의 번호이므로 이 변경에서 덮어쓰지 않는다.
6. 백엔드: `INTEGRATION_API_TOKEN`에 32자 이상의 서버 전용 비밀값을 지정한다.
   Minecraft 서버: 같은 값을 `NFS_INTEGRATION_API_TOKEN`에 지정한다.
   `backendBaseUrl` 또는 `NFS_BACKEND_BASE_URL`도 기존 방식대로 설정한다.
7. 백엔드는 `npm ci`, `npm start`. 외부 클라이언트에 기존 API 포트를 공개하지 않는다.
   새 `/integration` 경로에는 인증을 추가했지만 기존 경제 API 전체 인증 변경은 이번 범위가 아니다.

## 보상 처리

- `/nfsreward <온라인 플레이어> <FTB 보상 ID>`는 OP 권한 2 이상만 실행할 수 있다.
- 명령은 물품/돈을 직접 지급하지 않는다. 월드의 `nfs-rewards/`에 요청을 저장한 후 반환한다.
- HTTP 호출은 백그라운드에서 처리한다. 재시작·타임아웃 후에도 동일 requestId로 재전송한다.
- 백엔드가 UUID별 wallet 행을 잠그고 잔액·원장·수령 기록·감사 outbox를 한 트랜잭션에 저장한다.
- 반복 보상은 실제 경과 쿨다운을 사용한다. 1회 보상은 다른 requestId로 호출해도 재지급하지 않는다.
- `config/s2-rewards.json`에서 금액/쿨다운을 바꿀 수 있다. `S2_REWARD_POLICY_PATH`로 외부 파일도 지정 가능.
- 원본 중복 보상 두 ID는 최초 명시 금액 70으로 통일했다. 유료 등급 배율과 전 서버 보너스는 제거했다.
- 시작 퀘스트의 금전 명령은 같은 FTB ID의 custom 보상으로 교체했다.
- 반복 랜덤 전리품의 100 화폐 명령은 에메랄드 1개로 바꿨다. 무작위 전리품을 1회 퀘스트 ID로 지급하면
  두 번째 전리품부터 지급이 막히므로 직접 화폐 지급 경로를 만들지 않았다.
- 백엔드 불가 시 대기열 파일을 지우지 않는다. token/정책/DB 문제를 고친 뒤 자동 재전송된다.
- 보상 요청 저장 자체가 실패하면 KubeJS 핸들러가 오류를 발생시킨다. FTB 수령 상태와 재수령 동작은
  실제 게임 검증 목록에 포함돼 있다.

## 감사 기록

`AUDIT_LOG_DIR` 기본값은 `logs/audit`. `integration-YYYY-MM-DD.jsonl` 파일에 계속 추가한다.
outbox는 파일 기록/fsync가 성공한 뒤 처리 완료로 표시한다. 크래시 시 같은 로그가 중복될 수 있으므로
`auditId`로 구분한다. 자동 삭제하지 않는다. 아직 기존 상점·우편 등 전체 거래 감사 기록은 포함하지 않는다.

## 이번 단계 이후

판매 등록 영속화·원본 아이템 보존·상점/우편 복구, 상품 등록과 납품,
colony별 기금·권한, 창고 출하를 차례로 구현한다. 주식/마을 기금 브랜치는 임의 병합하지 않았다.

테스트: `npm test`, `python -m unittest discover -s tests -p 'test_*.py'`.
DB 테스트는 PGlite PostgreSQL 엔진에서 수행하며 실제 pg 서버의 다중 연결 경합 시험은 별도다.
