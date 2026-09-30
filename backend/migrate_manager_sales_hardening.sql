-- Manager sales hardening: purchase requests remain unprovisioned until Super Manager creates the panel.
ALTER TABLE subscription_payment_requests
    ALTER COLUMN manager_id DROP NOT NULL;
ALTER TABLE subscription_payment_requests
    ADD COLUMN IF NOT EXISTS buyer_phone VARCHAR(50),
    ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS buyer_gamenet_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS buyer_device_id VARCHAR(255);
ALTER TABLE manager_entitlements
    ADD COLUMN IF NOT EXISTS max_devices INTEGER NOT NULL DEFAULT 1;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'manager_entitlements_max_devices_chk'
    ) THEN
        ALTER TABLE manager_entitlements
            ADD CONSTRAINT manager_entitlements_max_devices_chk CHECK (max_devices BETWEEN 1 AND 100);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS manager_device_bindings (
    id BIGSERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (manager_id, device_id)
);
CREATE INDEX IF NOT EXISTS idx_manager_device_bindings_manager_active
    ON manager_device_bindings(manager_id, active);
