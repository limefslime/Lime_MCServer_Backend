BEGIN;
CREATE TABLE IF NOT EXISTS delivery_contracts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  player_id UUID NOT NULL REFERENCES players(id),
  template_id TEXT NOT NULL,
  definition JSONB NOT NULL,
  delivered INTEGER NOT NULL DEFAULT 0 CHECK (delivered >= 0),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','completed')),
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS delivery_one_active_template
  ON delivery_contracts(player_id,template_id) WHERE status='active';
CREATE INDEX IF NOT EXISTS delivery_player_history ON delivery_contracts(player_id,accepted_at DESC);
CREATE TABLE IF NOT EXISTS delivery_receipts (
  player_id UUID NOT NULL REFERENCES players(id),
  request_id UUID NOT NULL,
  operation TEXT NOT NULL,
  target TEXT NOT NULL,
  item_id TEXT NOT NULL,
  quantity INTEGER NOT NULL,
  result JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(player_id,request_id)
);
COMMIT;
