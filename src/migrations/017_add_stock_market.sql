-- 017_add_stock_market.sql
-- Stock-style invest foundation + seed rows

BEGIN;

ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'invest_stock_buy';
ALTER TYPE ledger_reason ADD VALUE IF NOT EXISTS 'invest_stock_sell';

CREATE TABLE IF NOT EXISTS stock (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE,
  current_price INTEGER NOT NULL CHECK (current_price > 0),
  previous_price INTEGER NOT NULL CHECK (previous_price > 0),
  volatility_type TEXT NOT NULL CHECK (volatility_type IN ('stable', 'normal', 'high')),
  min_price INTEGER NOT NULL CHECK (min_price > 0),
  max_price INTEGER NOT NULL CHECK (max_price >= min_price),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS player_stock (
  player_id UUID NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  stock_id UUID NOT NULL REFERENCES stock(id) ON DELETE CASCADE,
  quantity INTEGER NOT NULL CHECK (quantity >= 0),
  avg_buy_price INTEGER NOT NULL CHECK (avg_buy_price >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (player_id, stock_id)
);

CREATE INDEX IF NOT EXISTS idx_player_stock_player
  ON player_stock (player_id);

CREATE TABLE IF NOT EXISTS stock_price_history (
  id BIGSERIAL PRIMARY KEY,
  stock_id UUID NOT NULL REFERENCES stock(id) ON DELETE CASCADE,
  price INTEGER NOT NULL CHECK (price > 0),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stock_price_history_stock_time
  ON stock_price_history (stock_id, recorded_at DESC);

INSERT INTO stock (
  id,
  name,
  current_price,
  previous_price,
  volatility_type,
  min_price,
  max_price
)
VALUES
  ('8ed55153-c474-43e3-8dbe-4d431835fd8f', 'Green Grain', 120, 120, 'stable', 80, 180),
  ('6df1d95c-8306-48d9-a6f9-74e7018e018f', 'Harbor Route', 210, 210, 'normal', 120, 360),
  ('fd2ad8c7-a686-4663-93de-630f5b4eb2a7', 'Iron Works', 340, 340, 'high', 170, 620),
  ('f0f1f3de-2769-4131-b52c-b34eb6c52c27', 'Global Trade', 500, 500, 'normal', 300, 900)
ON CONFLICT (id) DO NOTHING;

INSERT INTO stock_price_history (stock_id, price, recorded_at)
SELECT s.id, s.current_price, NOW()
FROM stock s
WHERE NOT EXISTS (
  SELECT 1
  FROM stock_price_history h
  WHERE h.stock_id = s.id
);

COMMIT;
