-- 012_add_project_effects.sql
-- 프로젝트 완료 시 활성화되는 서버 전역 효과 저장 테이블

BEGIN;

CREATE TABLE IF NOT EXISTS project_effects (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL UNIQUE REFERENCES invest_projects(id) ON DELETE CASCADE,
  effect_type TEXT NOT NULL CHECK (effect_type IN ('price_bonus', 'xp_bonus', 'focus_bonus')),
  effect_target TEXT NOT NULL CHECK (effect_target IN ('agri', 'port', 'industry', 'global')),
  effect_value NUMERIC(10,2) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_project_effects_is_active
  ON project_effects (is_active);

COMMIT;
