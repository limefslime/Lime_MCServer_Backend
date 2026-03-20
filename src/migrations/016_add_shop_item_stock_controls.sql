-- 016_add_shop_item_stock_controls.sql
-- 상점 아이템 단일 재고 수량 + 소진 시 주기 충전 설정
BEGIN;

ALTER TABLE shop_items
  ADD COLUMN IF NOT EXISTS stock_quantity INTEGER NOT NULL DEFAULT 200
    CHECK (stock_quantity >= 0);

ALTER TABLE shop_items
  ADD COLUMN IF NOT EXISTS replenish_amount INTEGER NOT NULL DEFAULT 50
    CHECK (replenish_amount >= 0);

ALTER TABLE shop_items
  ADD COLUMN IF NOT EXISTS replenish_interval_seconds INTEGER NOT NULL DEFAULT 300
    CHECK (replenish_interval_seconds >= 0);

ALTER TABLE shop_items
  ADD COLUMN IF NOT EXISTS last_replenished_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_shop_items_stock_quantity
  ON shop_items (stock_quantity);

COMMIT;
