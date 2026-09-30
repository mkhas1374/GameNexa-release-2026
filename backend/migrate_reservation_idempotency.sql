BEGIN;
CREATE TABLE IF NOT EXISTS reservation_request_idempotency (
  id BIGSERIAL PRIMARY KEY,
  manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
  customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  idempotency_key VARCHAR(128) NOT NULL,
  reservation_ids JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(manager_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_reservation_request_idempotency_customer ON reservation_request_idempotency(manager_id, customer_id, created_at DESC);
COMMIT;
