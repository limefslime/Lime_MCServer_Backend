-- 008_add_region_progress.sql
-- 구역 XP/레벨 진행도 저장 테이블

BEGIN;

CREATE TABLE IF NOT EXISTS region_progress (
  region TEXT PRIMARY KEY CHECK (region IN ('agri', 'port', 'industry')),
  xp INTEGER NOT NULL DEFAULT 0 CHECK (xp >= 0),
  level INTEGER NOT NULL DEFAULT 1 CHECK (level >= 1),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO region_progress (region, xp, level, updated_at)
VALUES
  ('agri', 0, 1, NOW()),
  ('port', 0, 1, NOW()),
  ('industry', 0, 1, NOW())
ON CONFLICT (region) DO NOTHING;

COMMIT;
