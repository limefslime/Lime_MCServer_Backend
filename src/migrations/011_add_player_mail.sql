-- 011_add_player_mail.sql
-- 플레이어 우편함(코인 보상 메일) MVP

-- ledger reason 확장
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'mail_reward';

BEGIN;

CREATE TABLE IF NOT EXISTS player_mail (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  message TEXT,
  reward_amount INTEGER NOT NULL DEFAULT 0 CHECK (reward_amount >= 0),
  is_claimed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  claimed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_player_mail_player
  ON player_mail (player_id);

COMMIT;
