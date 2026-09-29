-- Add restriction and surcharge to customers
ALTER TABLE customers ADD COLUMN IF NOT EXISTS restricted_until timestamp with time zone;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pending_surcharge_percent numeric(5,2) DEFAULT 0;

-- Create lp_ledger if not exists
CREATE TABLE IF NOT EXISTS lp_ledger (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
