-- 014_add_project_reward_logs.sql
-- 프로젝트 완료 시 투자자 개인 보상 메일 중복 생성을 방지하기 위한 로그 테이블

BEGIN;

CREATE TABLE IF NOT EXISTS project_reward_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES invest_projects(id) ON DELETE CASCADE,
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  contribution_amount INTEGER NOT NULL CHECK (contribution_amount > 0),
  reward_amount INTEGER NOT NULL CHECK (reward_amount > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_project_reward_logs_project_player UNIQUE (project_id, player_id)
);

CREATE INDEX IF NOT EXISTS idx_project_reward_logs_project
  ON project_reward_logs (project_id);

COMMIT;
