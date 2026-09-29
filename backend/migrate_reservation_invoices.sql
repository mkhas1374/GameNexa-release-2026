BEGIN;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS reservation_id INTEGER REFERENCES reservations(id) ON DELETE RESTRICT;
ALTER TABLE invoices ALTER COLUMN session_id DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_invoices_manager_reservation ON invoices(manager_id, reservation_id) WHERE reservation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_invoices_manager_reservation ON invoices(manager_id, reservation_id);
ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS reservation_id INTEGER REFERENCES reservations(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_payment_transactions_manager_reservation ON payment_transactions(manager_id, reservation_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_reservation_success ON payment_transactions(manager_id, reservation_id, status);
COMMIT;
