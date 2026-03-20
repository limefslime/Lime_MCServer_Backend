-- 013_add_player_mail_lookup_index.sql
-- 플레이어 우편함 조회(player_id + 최신순 정렬) 최적화 인덱스

BEGIN;

CREATE INDEX IF NOT EXISTS idx_mail_player
  ON player_mail (player_id, created_at DESC);

COMMIT;
