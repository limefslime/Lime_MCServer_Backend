-- 007_add_shop_transactions_focus_index.sql
-- focus 재계산(최근 24시간 category 집계) 성능용 인덱스

CREATE INDEX IF NOT EXISTS idx_shop_tx_focus
  ON shop_transactions (category, created_at);
