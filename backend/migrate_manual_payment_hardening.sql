ALTER TABLE manual_payment_requests
  ADD COLUMN IF NOT EXISTS approved_amount NUMERIC(30,10),
  ADD COLUMN IF NOT EXISTS approved_currency VARCHAR(10),
  ADD COLUMN IF NOT EXISTS approved_payment_method_code VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_pending_manager
  ON manual_payment_requests(manager_id, status, created_at DESC)
  WHERE status='PENDING_MANAGER_REVIEW';

CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_customer_status
  ON manual_payment_requests(manager_id, customer_id, status, created_at DESC);
