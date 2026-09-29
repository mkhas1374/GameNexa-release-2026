BEGIN;

ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS session_id uuid;
ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS payment_transactions_session_id_fkey;
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_session_id_fkey FOREIGN KEY (session_id) REFERENCES game_sessions(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_payment_transactions_manager_session ON payment_transactions(manager_id, session_id);

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS settled_at timestamptz;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS settlement_idempotency_key varchar(100);
CREATE UNIQUE INDEX IF NOT EXISTS ux_invoices_settlement_idempotency ON invoices(manager_id, settlement_idempotency_key) WHERE settlement_idempotency_key IS NOT NULL;

COMMIT;
