-- 공용 지갑 + 원장(ledger) 1단계 마이그레이션
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type
    WHERE typname = 'ledger_reason'
  ) THEN
    CREATE TYPE ledger_reason AS ENUM (
      'shop_buy',
      'shop_sell',
      'event_reward',
      'admin',
      'mail_transfer',
      'quest_reward',
      'shop_purchase'
    );
  END IF;
END
$$;

CREATE TABLE IF NOT EXISTS players (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallets (
  player_id UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
  balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ledger (
  id BIGSERIAL PRIMARY KEY,
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  target_player_id UUID NULL REFERENCES players(id) ON DELETE SET NULL,
  type TEXT NOT NULL CHECK (type IN ('add', 'subtract')),
  amount INTEGER NOT NULL CHECK (amount > 0),
  reason ledger_reason NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_player_created_at
  ON ledger (player_id, created_at DESC);

COMMIT;
