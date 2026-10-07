BEGIN;
CREATE TABLE IF NOT EXISTS shop_trade_receipts (
  player_id UUID NOT NULL REFERENCES players(id),
  request_id UUID NOT NULL,
  transaction_type TEXT NOT NULL CHECK (transaction_type IN ('buy', 'sell')),
  item_id TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  result JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (player_id, request_id)
);
COMMIT;
