-- 002_wallet_schema_upgrade.sql
-- 기존(초기) 지갑 스키마를 최신 규칙으로 정렬한다.
-- 대상 변경:
-- 1) players.username unique
-- 2) ledger.target_player_id 추가
-- 3) ledger.reason TEXT -> ledger_reason ENUM

-- 1) ENUM 타입 보장
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

-- 타입이 이미 존재하는 경우에도 값 누락을 보완한다.
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'shop_buy';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'shop_sell';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'event_reward';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'admin';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'mail_transfer';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'quest_reward';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'shop_purchase';

-- 2) ledger.target_player_id 추가 및 FK 연결
ALTER TABLE ledger
  ADD COLUMN IF NOT EXISTS target_player_id UUID NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ledger_target_player_id_fkey'
  ) THEN
    ALTER TABLE ledger
      ADD CONSTRAINT ledger_target_player_id_fkey
      FOREIGN KEY (target_player_id)
      REFERENCES players(id)
      ON DELETE SET NULL;
  END IF;
END
$$;

-- 3) reason 컬럼을 ENUM으로 승격
-- 기존 데이터에 허용되지 않은 reason이 있으면 명시적으로 실패시킨다.
DO $$
DECLARE
  invalid_count INTEGER;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'ledger'
      AND column_name = 'reason'
      AND udt_name <> 'ledger_reason'
  ) THEN
    SELECT COUNT(*)
      INTO invalid_count
    FROM ledger
    WHERE reason IS NULL
       OR reason NOT IN (
         'shop_buy',
         'shop_sell',
         'event_reward',
         'admin',
         'mail_transfer',
         'quest_reward',
         'shop_purchase'
       );

    IF invalid_count > 0 THEN
      RAISE EXCEPTION
        'Cannot migrate ledger.reason to enum: % invalid rows found. Clean up reason values first.',
        invalid_count;
    END IF;

    ALTER TABLE ledger
      ALTER COLUMN reason TYPE ledger_reason
      USING reason::ledger_reason;
  END IF;
END
$$;

-- 4) players.username unique 보장
-- 중복 username이 있으면 인덱스 생성 전에 실패시켜 원인 파악을 쉽게 한다.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM players
    WHERE username IS NOT NULL
    GROUP BY username
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION
      'Cannot enforce username uniqueness: duplicate usernames exist in players.';
  END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_players_username_unique
  ON players(username)
  WHERE username IS NOT NULL;
