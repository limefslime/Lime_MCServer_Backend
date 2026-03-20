-- 015_add_events.sql
-- 이벤트 시스템 MVP: 생성/조회/활성화/종료 상태 관리

BEGIN;

CREATE TABLE IF NOT EXISTS events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  region TEXT NOT NULL CHECK (region IN ('agri', 'port', 'industry', 'global')),
  effect_type TEXT NOT NULL CHECK (effect_type IN ('price_bonus', 'xp_bonus')),
  effect_value NUMERIC(10,2) NOT NULL CHECK (effect_value > 0),
  status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'active', 'ended')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_events_status
  ON events (status);

COMMIT;
