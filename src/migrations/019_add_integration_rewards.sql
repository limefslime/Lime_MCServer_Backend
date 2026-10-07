BEGIN;
CREATE TABLE IF NOT EXISTS integration_reward_receipts (
  player_id UUID NOT NULL REFERENCES players(id),
  request_id UUID NOT NULL,
  reward_id TEXT NOT NULL,
  result JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (player_id, request_id)
);
CREATE TABLE IF NOT EXISTS integration_reward_state (
  player_id UUID NOT NULL REFERENCES players(id),
  reward_id TEXT NOT NULL,
  last_granted_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (player_id, reward_id)
);
CREATE TABLE IF NOT EXISTS integration_audit_outbox (
  id BIGSERIAL PRIMARY KEY,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  written_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS integration_audit_pending ON integration_audit_outbox(id)
  WHERE written_at IS NULL;
COMMIT;
