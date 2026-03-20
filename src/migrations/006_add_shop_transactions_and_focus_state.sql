-- 006_add_shop_transactions_and_focus_state.sql
-- focus 계산을 위한 판매 기록 테이블 + 현재 focus 상태 테이블

BEGIN;

CREATE TABLE IF NOT EXISTS shop_transactions (
  id BIGSERIAL PRIMARY KEY,
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  item_id TEXT NOT NULL REFERENCES shop_items(item_id),
  category TEXT NOT NULL CHECK (category IN ('agri', 'port', 'industry', 'misc')),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price INTEGER NOT NULL CHECK (unit_price >= 0),
  total_price INTEGER NOT NULL CHECK (total_price >= 0),
  transaction_type TEXT NOT NULL CHECK (transaction_type IN ('buy', 'sell')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS focus_state (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  focus_region TEXT NOT NULL CHECK (focus_region IN ('agri', 'port', 'industry')),
  calculated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 조회 API에서 null 처리를 줄이기 위해 기본 row를 하나 확보한다.
INSERT INTO focus_state (id, focus_region, calculated_at)
VALUES (1, 'agri', NOW())
ON CONFLICT (id) DO NOTHING;

COMMIT;
