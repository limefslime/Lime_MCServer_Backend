# S2 모드팩 연동 검토 — 2026-10-07

## 확인 범위와 한계

- 저장소: https://github.com/limefslime/Lime_MCServer_Backend
- 기본 브랜치 main: `3ac7be763073835536680d02aefe94c612277cbb`, 마지막 커밋 2026-09-25.
- 모든 원격 브랜치의 HEAD를 조회했다. 마을·주식 구현은 `feature/village-backend-clean`, `feature/village-client-clean`, `feature/stock-backend-clean`, `shop3` 등에 존재하지만 현재 main에는 해당 모듈이 없다. 브랜치명이나 날짜만으로 병합할 기준을 결정하면 안 된다.
- 10월 대화에서 언급한 개발 5~8단계, X06 등의 완료 상태를 이 저장소에서 확인할 수 없다. 별도 저장소/로컬 변경 여부는 미확인이다.
- 비교 대상은 CurseForge The Colony Network S2 1.4, 파일 ID 8528083. 실제 배포 ZIP의 manifest, modlist, overrides 설정·스크립트를 확인했다.
- 코드·설정의 정적 검토이며 서버 기동, DB 마이그레이션, 거래 재현 및 통합 테스트는 수행하지 않았다. 실행 오류 확정과 코드상 위험을 구분한다.

## 현재 구현

| 기능 | main에서 확인한 상태 | 근거 |
|---|---|---|
| 지갑·원장 | UUID 기반 플레이어 지갑, 증감·거래 원장 | src/modules/wallet, src/db/pool.js |
| 서버 상점 | 구매·판매·가격 미리보기, 재고·자동 보충, 거래 기록 | src/modules/shop |
| 가격 효과 | focus, 프로젝트 효과, 이벤트에 따른 가격 보정 | shop.service.js, shopEffect.service.js |
| 지역·공동 투자 | 지역 진행도, 프로젝트 투자·완료·효과 | src/modules/region, invest, project-completion |
| 우편 | 금전 보상·아이템 ID/수량 보상, 수령 처리 | src/modules/mail |
| 인게임 UI | 상점·우편·투자·상태·허브 및 HTTP 브리지 | namanseulfarming-template-1.21.1/src/main/java |
| 관리자 기능 | 요약 조회 API. 값을 편집하는 관리자 UI는 확인되지 않음 | src/modules/admin/admin.routes.js |
| 주식·마을 기금 | 별도 브랜치 구현. main의 서비스는 프로젝트 투자 중심 | feature/stock-backend-clean, feature/village-backend-clean |
| 플레이어 거래소 | main의 판매 등록은 UUID별 메모리 Map. 판매자 가격·구매자 결제·판매자 정산을 갖춘 공용 거래소로 보기 어려움 | PlayerShopListingService.java, UiServerPayloadHandlers.java |
| 로컬 감사 파일·요청 중복 방지 | main에서 전용 구현 미확인 | 전체 소스 검색 |
| 마인콜로니 직접 연동 | colony ID·회원 권한·창고·건물·Trade Post 연동 코드 미확인 | 전체 소스 검색 |

## S2 1.4 실물 확인

- manifest: Minecraft 1.21.1, NeoForge 21.1.244, 프로젝트 항목 223개. 이 숫자는 모드뿐 아니라 리소스팩 등 manifest 항목을 포함한다.
- 우리 빌드 설정은 NeoForge 21.1.219. 기본 버전·로더 계열은 맞지만 21.1.244 서버에서 실제 구동/패킷 검증이 필요하다.
- MineColonies, Trade Post, EconomyCraft, FTB Quests/Ranks, KubeJS, Minecolonies Questline, Spice of Life/주민 애드온, Mighty Mail 등이 포함된다.
- modlist에는 Farmer's Delight, Aquaculture, Create가 없다. 소개에 있는 철도 교역 표현을 Create 설치 증거로 취급하지 않는다. 이 셋을 원하는 경우 별도 추가·호환 검증 대상이다.
- EconomyCraft 설정: 시작금 1000, 일일보상 100, 일일 판매한도 1000, 세율 10%, PvP 잔액 손실 5%.
- KubeJS `CustomRewards.js`와 `ecoGive.js`가 `eco addmoney`를 실행한다. `ranks.js`는 FTB Ranks·태그를 바꾼다.
- `recettes.js`에서 타운홀·보급캠프/보급선 및 Trade Post 전초기지 관련 제작법을 제거한다. 도입 시 퀘스트로 획득하는 동선을 확인해야 한다.
- EconomyCraft balances/daily/daily_sells 데이터 파일이 배포에 포함된다. 새 서버 초기화 시 제작자 서버의 운영 데이터를 가져오지 않도록 정리한다. 개별 플레이어 데이터는 보고서에 노출하지 않는다.

## 먼저 보완할 항목

1. **기준 코드 확정**: main과 다른 브랜치 구현의 관계를 정리하고 10월 개발본이 있는 위치를 확인한다. 기존 작업을 누락한 채 재개발하지 않는다.
2. **권한 검사**: 팩 `ecoGive.js`의 balGive, `ranks.js`의 rankGive에 `.requires(...)` 권한 제한이 없다. 서버 명령 소스로 경제·등급 명령을 실행하므로 일반 사용자 호출 가능 여부를 우선 재현하고 OP 전용으로 제한한다. 우리 HTTP API에도 main에서 인증 미들웨어가 확인되지 않는다. 서버 전용 인증과 관리자 변경 권한이 필요하다.
3. **판매 등록 영속화**: main의 등록에서 인벤토리 물품을 제거한 뒤 메모리 Map에 기록한다. 재시작 때 등록 상태 소실 위험이 있다. DB/월드 저장과 원본 스택 보관이 먼저다.
4. **아이템 구성요소 보존**: 아이템 비교는 item type만 보고, 지급은 `new ItemStack(item, count)`로 재생성한다. 이름·인챈트·내구도·내용물 등 원본 Data Components가 보존되지 않는다. 거래소·반환 우편에는 원본 스택 직렬화를 사용하고, 기본 상점에서 특수 스택 판매를 허용할지는 별도 정책으로 정한다.
5. **거래 복구**: backend 금전 거래와 Minecraft 인벤토리 조작이 한 트랜잭션이 아니다. 응답 유실/재시도/크래시 때 돈·물품 불일치가 생길 수 있다. 거래 UUID, 멱등 처리, 물품/돈 예치 및 복구 상태를 기록한다. DB 내부 지갑·원장 트랜잭션은 기존 구조를 재사용한다.
6. **우편 지급 확인**: 백엔드에서 수령 처리된 뒤 게임 물품 지급이 진행된다. 지급 확인·재개 가능한 상태를 추가한다. main의 수령 API에는 playerId가 없으며 브리지에서만 소유자를 검사하므로 API 경계에서도 소유권을 확인한다.
7. **지연·감사 기록**: HTTP_CLIENT.send 동기 호출은 네트워크 핸들러 경로에 있다. 실제 실행 스레드를 확인하고 HTTP는 비동기, 인벤토리 변경은 서버 스레드로 구분한다. 감사 파일은 경제 기록의 별도 내구성 경로로 마련한다.
8. **팩 스크립트 정리**: CustomRewards.js의 두 보상 ID가 두 번 등록돼 있다. 중복 지급/덮어쓰기 여부를 확인한다. balGive는 scoreboard trackedPlayers 전체에 보너스를 지급하므로 대상과 총 발행량을 재설계한다. 유료 등급·프랑스어 서버 안내와 보상 배율은 우리 서버 규칙으로 대체한다.

## 연결 방향 권고

| 대상 | 권고 | 이유/주의 |
|---|---|---|
| EconomyCraft | 우리 백엔드 지갑을 플레이어 화폐 원장으로 사용하고 중복 상점·일일보상·지급 명령을 대체 | 양쪽 잔액을 주기적으로 맞추면 충돌·이중 지급 위험. 모드 삭제 전 퀘스트/스크립트 의존성 검사 |
| FTB Quests/KubeJS | UUID + 보상 ID + 반복 회차로 중복 없는 보상 요청을 우리 브리지에 전달 | 기존 `eco addmoney`의 대체 경로. 보상 배율은 승인된 서버 규칙만 적용 |
| Trade Post | 초기에는 내부 무역 코인을 독립 운영. 플레이어 돈과 직접 환전은 보류 | 무역·주민 급여 등 모드 내부 경제를 우선 보존. 지원 API/코인 생성과 사용 경로를 설치 버전 기준으로 조사해야 함 |
| MineColonies | world/server ID + colony ID + UUID 회원·역할 매핑, 서버에서 권한 검증 | 플레이어 지갑과 마을 공동 자산 구분. 마을 탈퇴·양도·삭제 정책 필요 |
| 마을 기금 | 기존 브랜치 기금은 id=1 단일 공용 기금. 여러 마을을 지원하면 colony별 기금으로 확장 | MineColonies 마을별 소유권 모델과 동일하지 않음 |
| Mighty Mail | 역할 분리: 일반 편지/물품 우편과 우리 거래 정산·시스템 보상을 구분 | 동일 보상을 두 우편 경로에 복제하지 않음 |
| 팩 상품 | 실제 레지스트리 ID/태그를 가져와 판매 승인·가격·상한을 관리자 설정으로 관리 | 현재 seed는 샘플 5개이며 팩에 없는 aquaculture:atlantic_cod 포함 |

EconomyCraft의 현재 제작자 문서에는 개발 API가 있지만 S2가 고정한 이전 파일에 같은 API가 있다는 증거는 없다. 설치 버전의 실제 API 확인 전 연동 가능성을 확정하지 않는다.

## 추가 기능 후보

| 우선도 | 후보 | 구체적인 행동 |
|---|---|---|
| 높음 | 농사·요리 확장 | Farmer's Delight 추가, 작물/요리 판매표와 납품 보상, 주민 식단·제작 호환 확인 |
| 높음 | 낚시 확장 | Aquaculture 추가, 어종 판매표·낚시 의뢰, NPC 어부 호환 별도 확인 |
| 높음 | 공용 플레이어 거래소 | 판매 가격 지정, 원본 물품 예치, 판매자 정산, 취소/만료/오프라인 수령 |
| 높음 | 납품 계약/주문 게시판 | 마을이 건축 재료·음식을 주문하고 플레이어가 납품. 수량/기한/보상 예치 |
| 높음 | 마을 기금·지출 권한 | 회원 기부, 역할별 인출/지출, 거래 원장·감사 파일 |
| 중간 | 창고 수동 출하 | 처음에는 권한 확인된 출하함에 넣은 물품만 판매. 창고 직접 자동 판매는 후속 단계 |
| 중간 | 재고 예약·마을 수요 | 건축/주민 식량용 최소 재고를 남기고 초과분만 판매 |
| 중간 | 경제 관리 UI | 상품·가격·판매한도·세율·보상·환전 정책을 관리자 설정으로 조정 |
| 중간 | 마을 경제 요약 | 기금, 매출/지출, 주요 납품과 재고 상태 표시 |
| 후속 | Trade Post 환전·자동 교역 정산 | API와 경제 악순환 검증 후 제한·수수료·일일 상한으로 도입 |
| 후속 | Create 철도·공장 | 원하는 경우 추가하며 S2 기본 포함으로 가정하지 않음 |

자동생산품을 획득 경로 기록 없이 플레이어 생산품과 정확히 구별할 수 있다고 가정하면 안 된다. 초기에는 모든 판매에 상품별/UUID별/마을별 상한·재고 기반 가격 정책을 사용하고, 출하함 경로를 구분해 기록한다.

## 개발 순서와 확인 기준

1. 기준 브랜치·팩 버전 확정, 팩 운영 데이터와 권한/등급 스크립트 정리.
2. 등록 영속화·아이템 보존·거래 멱등/복구·API 인증·감사 기록.
3. 플레이어 화폐 원장 단일화 및 FTB 보상 연결. Trade Post 코인은 독립 유지.
4. 농사/낚시 추가, 품목 등록·경제 정책 설정.
5. 공용 거래소·납품 계약·colony별 기금과 역할 연결.
6. 창고 출하·수요 연동, 필요할 때 Trade Post 환전/자동 교역 확장.

검증 체크리스트:
- 일반 사용자는 balGive/rankGive·관리자 변경 명령/API를 실행할 수 없어야 한다.
- 동일 거래/보상 재시도, 서버 재시작, HTTP 응답 유실 시 돈과 물품을 이중 지급하거나 잃지 않아야 한다.
- 이름·인챈트·내구도·컨테이너 내용이 거래/취소/우편 후 동일해야 한다.
- 다른 플레이어의 우편·등록·마을 기금에 접근할 수 없어야 한다.
- 오프라인 판매자, 가득 찬 인벤토리, 마을 탈퇴/양도/삭제에서 복구 경로가 있어야 한다.
- Trade Post·서버 상점·가공 레시피·보상 간 반복 거래로 무제한 이익이 발생하지 않아야 한다.
- 최소 식량·건축 재고가 출하로 소모되지 않아야 한다.
- 회원 수/마을 규모를 늘린 서버에서 거래 중 tick 지연을 측정한다.

## 자료

- https://www.curseforge.com/minecraft/modpacks/the-colony-network-s2
- https://www.curseforge.com/minecraft/modpacks/the-colony-network-s2/files/8528083
- S2 1.4 ZIP: manifest.json, modlist.html, overrides/config/economycraft, overrides/kubejs/server_scripts
- https://www.curseforge.com/minecraft/mc-mods/economycraft-mp
- https://www.curseforge.com/minecraft/mc-mods/mc-trade-post
- https://www.curseforge.com/minecraft/mc-mods/minecolonies-questline

코드 변경·브랜치 병합·원격 푸시는 수행하지 않았다. 이 문서는 검토 결과만 정리한다.
