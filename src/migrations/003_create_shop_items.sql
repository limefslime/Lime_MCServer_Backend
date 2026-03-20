-- 003_create_shop_items.sql
-- 상점 기본 상품 테이블 생성

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_type
    WHERE typname = 'shop_category'
  ) THEN
    CREATE TYPE shop_category AS ENUM ('agri', 'port', 'industry', 'misc');
  END IF;
END
$$;

CREATE TABLE IF NOT EXISTS shop_items (
  id BIGSERIAL PRIMARY KEY,
  item_id TEXT NOT NULL UNIQUE,
  item_name TEXT NOT NULL,
  category shop_category NOT NULL,
  buy_price INTEGER NOT NULL CHECK (buy_price >= 0),
  sell_price INTEGER NOT NULL CHECK (sell_price >= 0),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shop_items_category
  ON shop_items (category);

CREATE INDEX IF NOT EXISTS idx_shop_items_is_active
  ON shop_items (is_active);

COMMIT;
