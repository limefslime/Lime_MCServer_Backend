-- 009_region_enum_and_region_index.sql
-- 1) region_progress.region 을 ENUM으로 승격
-- 2) region 집계 쿼리 성능용 인덱스 추가

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type
    WHERE typname = 'region_type'
  ) THEN
    CREATE TYPE region_type AS ENUM ('agri', 'port', 'industry');
  END IF;
END
$$;

-- 기존 TEXT+CHECK 구조를 ENUM으로 변경
ALTER TABLE region_progress
  DROP CONSTRAINT IF EXISTS region_progress_region_check;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'region_progress'
      AND column_name = 'region'
      AND udt_name <> 'region_type'
  ) THEN
    ALTER TABLE region_progress
      ALTER COLUMN region TYPE region_type
      USING region::region_type;
  END IF;
END
$$;

-- region 재계산 쿼리 최적화: WHERE transaction_type='sell' + GROUP BY category
CREATE INDEX IF NOT EXISTS idx_shop_tx_region
  ON shop_transactions (transaction_type, category);

COMMIT;
