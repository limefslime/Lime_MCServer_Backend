package com.namanseul.farmingmod.network;

/** Display translation only; never applied to item names, JSON keys or receipt identities. */
public final class UiKoreanText {
    private UiKoreanText() {}
    public static String value(String value) {
        if(value==null||value.isBlank())return "없음";
        return switch(value){
            case "players"->"플레이어·잔액 조회";
            case "wallet_add"->"돈 지급";
            case "wallet_subtract"->"돈 차감";
            case "wallet_set"->"잔액 직접 설정";
            case "wallet_all"->"등록된 전체 플레이어에게 지급";
            case "player_freeze"->"경제 이용 정지 설정";
            case "shop_list"->"상품 조회";
            case "shop_save"->"상품 등록·수정";
            case "shop_disable"->"상품 판매 중지";
            case "rules_get"->"경제 설정 조회";
            case "rules_save"->"경제 설정 변경";
            case "deliveries"->"납품 의뢰 정책 조회";
            case "delivery_save"->"납품 의뢰 등록·수정";
            case "delivery_disable"->"납품 의뢰 모집 중지";
            case "contracts"->"수락한 납품 의뢰 조회";
            case "delivery_cancel"->"의뢰 취소·납품 아이템 반환";
            case "rewards"->"보상 정책 조회";
            case "reward_save"->"보상 정책 등록·수정";
            case "reward_reset"->"보상 수령 제한 초기화";
            case "mail_list"->"플레이어 우편 조회";
            case "mail_send"->"돈·아이템 복구 우편 발송";
            case "mail_all"->"등록된 전체 플레이어에게 우편 발송";
            case "mail_cancel"->"미수령 우편 취소";
            case "projects"->"프로젝트";
            case "project_save"->"프로젝트 등록·수정";
            case "project_complete"->"프로젝트 완료·보상 발급";
            case "project_refund"->"프로젝트 취소·기여금 환불";
            case "project_effect"->"프로젝트 효과 변경";
            case "events"->"이벤트 조회";
            case "event_save"->"이벤트 등록·수정";
            case "event_end"->"이벤트 종료";
            case "regions"->"구역 진행도 조회";
            case "region_set"->"구역 경험치 설정";
            case "focus_set"->"집중 구역 설정";
            case "trade_refund"->"원본 구매 내역 1회 환불";
            case "game_pending"->"미완료 거래·등록 아이템 조회";
            case "game_retry"->"미완료 거래 재처리";
            case "listing_return"->"보관 중인 등록 아이템 반환";
            case "permission_set"->"OP 경제 관리 권한 설정";
            case "health"->"DB·감사 기록 상태";
            case "active"->"진행 중";case "completed"->"완료";case "failed"->"실패";case "cancelled"->"취소";case "draft"->"준비 중";case "ended"->"종료";
            case "money","currency","gold"->"돈";case "item","items"->"아이템";case "money_and_item","mixed"->"돈과 아이템";case "notification","none"->"알림";case "reward"->"보상";case "project_completion","Project Completion"->"프로젝트 완료";case "project_reward","Project Reward"->"프로젝트 보상";case "Delivery return"->"납품 아이템 반환";case "system"->"시스템";
            case "agri","farming"->"농업";case "port","fishing"->"어업";case "industry","mining"->"산업";case "global"->"전체 구역";case "misc"->"기타";
            case "price_bonus"->"가격 보정";case "xp_bonus"->"경험치 보정";case "focus_bonus"->"집중 구역 보정";case "true"->"예";case "false"->"아니오";case "unknown"->"확인 불가";
            default->value;
        };
    }
    public static String error(String error) {
        if(error==null||error.isBlank())return "처리하지 못했습니다. 새로고침 후 다시 시도하세요.";
        boolean pending=error.startsWith("[PENDING]");String raw=pending?error.substring(9).trim():error;
        String translated=switch(raw){
            case "not found","mail not found","Mail not found"->"항목을 찾을 수 없습니다.";
            case "insufficient balance","Insufficient balance"->"잔액이 부족합니다.";
            case "inventory full; free space before buying"->"인벤토리에 빈칸을 만든 뒤 구매하세요.";
            case "not enough items in main inventory"->"인벤토리에 아이템이 부족합니다.";
            case "quantity must be positive","quantity must be a positive integer","Amount must be positive"->"1 이상의 정수를 입력하세요.";
            case "sell quantity must be 1000 or less"->"한 번에 최대 1,000개까지 판매할 수 있습니다.";
            case "Cancel existing listing before changing its price"->"등록 가격을 바꾸려면 기존 등록을 먼저 취소하세요.";
            case "Listing price must be positive","Invalid listing price"->"개당 가격은 1원 이상이어야 합니다.";
            case "A trade is still pending. Wait for recovery before changing listings.","An earlier trade is being recovered. Do not submit a new trade.","An earlier trade or mail claim is still being recovered."->"이전 거래를 복구 중입니다. 완료될 때까지 기다려 주세요.";
            case "Trade confirmation unavailable; original request remains queued.","Mail confirmation unavailable; original claim remains queued.","delivery confirmation unavailable"->"결과 확인을 기다리는 중입니다. 원래 요청을 보관했습니다.";
            case "Project request already processing"->"프로젝트 요청을 처리 중입니다.";
            case "backendBaseUrl not configured"->"백엔드 연결 주소를 확인하세요.";
            case "shop backend request failed","mail backend request failed","invest backend request failed"->"백엔드에 연결하지 못했습니다. 연결 상태를 확인하세요.";
            case "mail owner mismatch"->"본인의 우편만 수령할 수 있습니다.";
            case "Unusable recovery item data; mail retained","Recovery item identity mismatch"->"아이템 복구 데이터를 확인하지 못했습니다. 우편을 보관했습니다.";
            case "Invalid UUID"->"선택한 ID가 올바르지 않습니다. 돋보기로 다시 선택하세요.";
            default->raw.codePoints().anyMatch(c->c>=0xAC00&&c<=0xD7A3)?raw:"결과를 확인하지 못했습니다. 새로고침 후 다시 시도하거나 서버 기록을 확인하세요.";
        };
        return (pending?"[PENDING] ":"")+translated;
    }
}
