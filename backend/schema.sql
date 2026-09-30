-- Create necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Managers
CREATE TABLE IF NOT EXISTS managers (
    id VARCHAR(50) PRIMARY KEY, -- Using VARCHAR to match legacy managerId if needed
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    token VARCHAR(255),
    display_name VARCHAR(255),
    gamenet_name VARCHAR(255),
    phone VARCHAR(50),
    plan_type VARCHAR(50),
    subscription_status VARCHAR(50) DEFAULT 'ACTIVE',
    payment_status VARCHAR(50) DEFAULT 'PAID',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE managers ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE managers ADD COLUMN IF NOT EXISTS gamenet_name VARCHAR(255);
ALTER TABLE managers ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE managers ADD COLUMN IF NOT EXISTS plan_type VARCHAR(50);
ALTER TABLE managers ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE managers ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50) DEFAULT 'PAID';
UPDATE managers SET phone = username WHERE phone IS NULL;
UPDATE managers SET display_name = username WHERE display_name IS NULL;

-- 2. Configuration Revisions
CREATE TABLE IF NOT EXISTS configuration_revisions (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    settings JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (manager_id, version_number)
);

-- 3. Customers
CREATE TABLE IF NOT EXISTS customers (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    phone_number VARCHAR(20) NOT NULL,
    full_name VARCHAR(150),
    club_tier VARCHAR(50) DEFAULT 'BRONZE',
    wallet_balance NUMERIC(15, 2) DEFAULT 0.00,
    gn_balance INT DEFAULT 0,
    lp_balance INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(manager_id, phone_number)
);

-- 4. Stations
CREATE TABLE IF NOT EXISTS stations (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    reservable BOOLEAN DEFAULT TRUE,
    controller_capacity INT DEFAULT 1,
    console_type VARCHAR(50) DEFAULT 'PS4',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(manager_id, name)
);

-- 5. Reservations
CREATE TABLE IF NOT EXISTS reservations (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    station_id INT REFERENCES stations(id) ON DELETE SET NULL, -- Can be NULL for full hall
    type VARCHAR(50) NOT NULL CHECK (type IN ('NORMAL_RESERVATION', 'FULL_HALL', 'EXCLUSIVE_FULL_DAY')),
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL,
    config_revision_id INT REFERENCES configuration_revisions(id) ON DELETE RESTRICT,
    
    -- Snapshot Fields (Immutable details at the time of creation)
    snap_base_price NUMERIC(30, 10) NOT NULL,
    snap_discount_amount NUMERIC(30, 10) DEFAULT 0.00,
    snap_final_price NUMERIC(30, 10) NOT NULL,
    snap_payable_amount NUMERIC(30, 10) NOT NULL,
    snap_deposit_amount NUMERIC(30, 10) DEFAULT 0.00,
    snap_currency VARCHAR(10) DEFAULT 'IRT',
    snap_pricing_version INT,
    snap_cancellation_policy JSONB,
    snap_gn_policy JSONB,
    snap_lp_policy JSONB,
    snap_wallet_policy JSONB,
    snap_restriction JSONB,
    snap_surcharge JSONB,
    snap_vip_policy JSONB,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for concurrency/overlapping checks
CREATE INDEX idx_reservations_time ON reservations(manager_id, station_id, start_time, end_time);

-- 6. Reservation Allocations (if a reservation spans multiple stations, e.g., Full Hall)
CREATE TABLE IF NOT EXISTS reservation_allocations (
    id SERIAL PRIMARY KEY,
    reservation_id INT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    station_id INT NOT NULL REFERENCES stations(id) ON DELETE CASCADE,
    UNIQUE(reservation_id, station_id)
);

-- 7. Payment Transactions
CREATE TABLE IF NOT EXISTS payment_transactions (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    reservation_id INT REFERENCES reservations(id) ON DELETE CASCADE,
    amount NUMERIC(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. Wallet Transactions
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount NUMERIC(15, 2) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 9. GN Ledger
CREATE TABLE IF NOT EXISTS gn_ledger (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount INT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 10. LP Ledger
CREATE TABLE IF NOT EXISTS lp_ledger (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount INT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 11. Reservation Audit
CREATE TABLE IF NOT EXISTS reservation_audit_logs (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    reservation_id INT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    actor VARCHAR(100) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 12. Global Idempotency (For API layer general idempotency if needed beyond transaction tables)
CREATE TABLE IF NOT EXISTS api_idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    scope VARCHAR(50) NOT NULL,
    response_body JSONB,
    response_status INT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 13. Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS trial_devices (
    device_id VARCHAR(255) PRIMARY KEY,
    device_fingerprint VARCHAR(255),
    device_name VARCHAR(255),
    started_at TIMESTAMPTZ DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    status VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, BANNED
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trial_devices_fingerprint
    ON trial_devices(device_fingerprint)
    WHERE device_fingerprint IS NOT NULL;

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

CREATE TABLE IF NOT EXISTS subscription_payment_requests (
    id SERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    plan_id VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    payment_reference VARCHAR(255),
    receipt_reference VARCHAR(255),
    customer_note TEXT,
    status VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    rejection_reason TEXT,
    reviewed_at TIMESTAMPTZ,
    reviewed_by VARCHAR(50) REFERENCES managers(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
ALTER TABLE subscription_payment_requests ADD COLUMN IF NOT EXISTS provisioned_account BOOLEAN NOT NULL DEFAULT FALSE;

-- Manual payment workflow: customer request never mutates financial state until Manager approval.
CREATE TABLE IF NOT EXISTS manager_payment_methods (id BIGSERIAL PRIMARY KEY, manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE, method_code VARCHAR(50) NOT NULL, display_name VARCHAR(120) NOT NULL, instructions TEXT, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), UNIQUE(manager_id, method_code));
CREATE TABLE IF NOT EXISTS manual_payment_requests (id BIGSERIAL PRIMARY KEY, manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE, customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE, reservation_id INT REFERENCES reservations(id) ON DELETE SET NULL, invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL, purpose VARCHAR(40) NOT NULL CHECK (purpose IN ('WALLET_TOPUP','RESERVATION_PAYMENT','MANUAL_GN','MANUAL_LP')), amount NUMERIC(30,10), currency VARCHAR(10) NOT NULL DEFAULT 'IRT', payment_method_id BIGINT REFERENCES manager_payment_methods(id) ON DELETE SET NULL, payment_method_code VARCHAR(50), payment_reference VARCHAR(255), receipt_reference VARCHAR(255), customer_note TEXT, manager_note TEXT, status VARCHAR(40) NOT NULL DEFAULT 'PENDING_MANAGER_REVIEW' CHECK (status IN ('PENDING_MANAGER_REVIEW','APPROVED','REJECTED','CANCELLED')), rejection_reason TEXT, reviewed_by VARCHAR(50) REFERENCES managers(id) ON DELETE SET NULL, reviewed_at TIMESTAMPTZ, idempotency_key VARCHAR(128) NOT NULL, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
CREATE UNIQUE INDEX IF NOT EXISTS uq_manual_payment_request_idempotency ON manual_payment_requests(manager_id, customer_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_manager_status ON manual_payment_requests(manager_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_customer ON manual_payment_requests(manager_id, customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_reservation ON manual_payment_requests(manager_id, reservation_id);
CREATE INDEX IF NOT EXISTS idx_manager_payment_methods_active ON manager_payment_methods(manager_id, active);

-- Manual payment hardening fields
ALTER TABLE manual_payment_requests ADD COLUMN IF NOT EXISTS approved_amount NUMERIC(30,10);
ALTER TABLE manual_payment_requests ADD COLUMN IF NOT EXISTS approved_currency VARCHAR(10);
ALTER TABLE manual_payment_requests ADD COLUMN IF NOT EXISTS approved_payment_method_code VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_pending_manager ON manual_payment_requests(manager_id, status, created_at DESC) WHERE status='PENDING_MANAGER_REVIEW';
CREATE INDEX IF NOT EXISTS idx_manual_payment_requests_customer_status ON manual_payment_requests(manager_id, customer_id, status, created_at DESC);
