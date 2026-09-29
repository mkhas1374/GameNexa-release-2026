-- Unconfirmed VIP requests do not consume capacity. Confirmed VIPs arbitrate capacity transactionally.
CREATE EXTENSION IF NOT EXISTS btree_gist;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM reservations a
        JOIN reservations b
          ON a.manager_id=b.manager_id
         AND a.station_id=b.station_id
         AND a.id < b.id
         AND a.start_time < b.end_time
         AND a.end_time > b.start_time
        WHERE a.status NOT IN ('CANCELLED','EXPIRED','NO_SHOW','REJECTED','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY','VIP_PENDING_PAYMENT','VIP_PAYMENT_PAID')
          AND b.status NOT IN ('CANCELLED','EXPIRED','NO_SHOW','REJECTED','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY','VIP_PENDING_PAYMENT','VIP_PAYMENT_PAID')
    ) THEN
        RAISE EXCEPTION 'RESERVATION_OVERLAP_DATA_MUST_BE_RESOLVED_BEFORE_CONSTRAINT_INSTALLATION';
    END IF;
END $$;
ALTER TABLE reservations DROP CONSTRAINT IF EXISTS no_overlap;
ALTER TABLE reservations ADD CONSTRAINT no_overlap EXCLUDE USING gist (
    manager_id WITH =,
    station_id WITH =,
    tstzrange(start_time, end_time) WITH &&
) WHERE (status NOT IN ('CANCELLED','EXPIRED','NO_SHOW','REJECTED','SUPERSEDED_BY_VIP','SUPERSEDED_BY_VIP_PRIORITY','VIP_PENDING_PAYMENT','VIP_PAYMENT_PAID'));
