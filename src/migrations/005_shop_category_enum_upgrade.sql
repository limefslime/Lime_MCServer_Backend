-- 005_shop_category_enum_upgrade.sql
-- 기존 shop_items.category(TEXT)를 shop_category ENUM으로 전환

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

ALTER TYPE shop_category ADD VALUE IF NOT EXISTS 'agri';
ALTER TYPE shop_category ADD VALUE IF NOT EXISTS 'port';
ALTER TYPE shop_category ADD VALUE IF NOT EXISTS 'industry';
ALTER TYPE shop_category ADD VALUE IF NOT EXISTS 'misc';

DO $$
DECLARE
  invalid_count INTEGER;
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'shop_items'
      AND column_name = 'category'
      AND udt_name <> 'shop_category'
  ) THEN
    SELECT COUNT(*)
      INTO invalid_count
    FROM shop_items
    WHERE category IS NULL
       OR category NOT IN ('agri', 'port', 'industry', 'misc');

    IF invalid_count > 0 THEN
      RAISE EXCEPTION
        'Cannot migrate shop_items.category to enum: % invalid rows found.',
        invalid_count;
    END IF;

    ALTER TABLE shop_items
      ALTER COLUMN category TYPE shop_category
      USING category::shop_category;
  END IF;
END
$$;
