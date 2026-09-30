-- Super Manager has a dedicated permanent entitlement, separate from paid Manager subscriptions.
ALTER TABLE manager_entitlements DROP CONSTRAINT IF EXISTS manager_entitlements_entitlement_type_check;
ALTER TABLE manager_entitlements ADD CONSTRAINT manager_entitlements_entitlement_type_check
    CHECK (entitlement_type IN ('TRIAL','SUBSCRIPTION','SUPER_MANAGER_LIFETIME'));

ALTER TABLE manager_entitlements DROP CONSTRAINT IF EXISTS manager_entitlements_plan_id_check;
ALTER TABLE manager_entitlements ADD CONSTRAINT manager_entitlements_plan_id_check
    CHECK (plan_id IN ('TRIAL_24H','1_MONTH','3_MONTHS','12_MONTHS','SUPER_MANAGER_LIFETIME'));

INSERT INTO manager_entitlements
    (manager_id, entitlement_type, plan_id, status, starts_at, expires_at, source, max_devices, metadata)
SELECT
    'mgr_super_admin',
    'SUPER_MANAGER_LIFETIME',
    'SUPER_MANAGER_LIFETIME',
    'ACTIVE',
    NOW(),
    TIMESTAMPTZ '9999-12-31 23:59:59+00',
    'SUPER_MANAGER',
    100,
    jsonb_build_object('permanent', true, 'reason', 'SUPER_MANAGER_SYSTEM_ENTITLEMENT')
WHERE EXISTS (SELECT 1 FROM managers WHERE id='mgr_super_admin')
  AND NOT EXISTS (
      SELECT 1 FROM manager_entitlements
      WHERE manager_id='mgr_super_admin'
        AND entitlement_type='SUPER_MANAGER_LIFETIME'
        AND status='ACTIVE'
  );

UPDATE managers
SET plan_type='SUPER_MANAGER_LIFETIME',
    subscription_status='ACTIVE',
    payment_status='EXEMPT',
    updated_at=NOW()
WHERE id='mgr_super_admin';
