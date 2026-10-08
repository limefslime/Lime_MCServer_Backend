BEGIN;
CREATE TABLE IF NOT EXISTS mail_claim_receipts (
  player_id UUID NOT NULL REFERENCES players(id),
  request_id UUID NOT NULL,
  mail_id UUID NOT NULL UNIQUE REFERENCES player_mail(id),
  result JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (player_id, request_id)
);
COMMIT;
