CREATE TABLE IF NOT EXISTS trial_device_blocks (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255),
    device_fingerprint VARCHAR(255),
    reason VARCHAR(50) NOT NULL DEFAULT 'DELETED_BY_SUPER_MANAGER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trial_device_blocks_identity_chk CHECK (device_id IS NOT NULL OR device_fingerprint IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_trial_device_blocks_device_id ON trial_device_blocks(device_id) WHERE device_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_trial_device_blocks_fingerprint ON trial_device_blocks(device_fingerprint) WHERE device_fingerprint IS NOT NULL;
CREATE TABLE IF NOT EXISTS trial_device_audit_log (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(255),
    device_fingerprint VARCHAR(255),
    action VARCHAR(30) NOT NULL,
    actor_manager_id VARCHAR(50),
    previous_started_at TIMESTAMPTZ,
    previous_expires_at TIMESTAMPTZ,
    new_started_at TIMESTAMPTZ,
    new_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trial_device_audit_created_at ON trial_device_audit_log(created_at DESC);
