-- 018_add_village_fund.sql
-- Village fund + player contribution

BEGIN;

ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'village_donation';

CREATE TABLE IF NOT EXISTS village_fund (
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  total_amount BIGINT NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
  level INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1),
  next_level_requirement BIGINT NOT NULL CHECK (next_level_requirement > 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS player_contribution (
  player_id UUID PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
  total_contribution BIGINT NOT NULL DEFAULT 0 CHECK (total_contribution >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO village_fund (id, total_amount, level, next_level_requirement)
VALUES (1, 0, 1, 10000)
ON CONFLICT (id) DO NOTHING;

COMMIT;
