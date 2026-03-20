-- 010_add_invest_mvp.sql
-- 투자 MVP: 프로젝트/기여 기록 테이블 + ledger reason 확장

-- 기존 enum에 투자 reason 추가
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'invest_project';

BEGIN;

CREATE TABLE IF NOT EXISTS invest_projects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  description TEXT,
  target_amount INTEGER NOT NULL CHECK (target_amount > 0),
  current_amount INTEGER NOT NULL DEFAULT 0 CHECK (current_amount >= 0),
  region TEXT NOT NULL CHECK (region IN ('agri', 'port', 'industry', 'global')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ends_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS invest_contributions (
  id BIGSERIAL PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES invest_projects(id) ON DELETE CASCADE,
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  amount INTEGER NOT NULL CHECK (amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invest_projects_status
  ON invest_projects (status);

CREATE INDEX IF NOT EXISTS idx_invest_contributions_project
  ON invest_contributions (project_id);

COMMIT;
