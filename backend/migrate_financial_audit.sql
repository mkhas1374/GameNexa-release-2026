CREATE TABLE IF NOT EXISTS financial_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT REFERENCES customers(id) ON DELETE SET NULL,
    invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL,
    reservation_id INT REFERENCES reservations(id) ON DELETE SET NULL,
    payment_id INT REFERENCES payment_transactions(id) ON DELETE SET NULL,
    event_type VARCHAR(60) NOT NULL,
    amount NUMERIC(30,10),
    currency VARCHAR(10),
    actor VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(128),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_financial_audit_manager_created ON financial_audit_logs(manager_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_financial_audit_invoice ON financial_audit_logs(manager_id, invoice_id);
CREATE INDEX IF NOT EXISTS idx_financial_audit_reservation ON financial_audit_logs(manager_id, reservation_id);
CREATE UNIQUE INDEX IF NOT EXISTS financial_audit_idempotency_key_key ON financial_audit_logs(manager_id, event_type, idempotency_key) WHERE idempotency_key IS NOT NULL;
