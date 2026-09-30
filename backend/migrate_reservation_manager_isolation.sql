-- Reservation tenant isolation defense-in-depth.
-- Existing known QA fixture 147 is reconciled before these constraints are installed.
UPDATE reservations r
SET manager_id = c.manager_id, updated_at = NOW()
FROM customers c
WHERE r.customer_id = c.id
  AND r.manager_id <> c.manager_id
  AND r.id = 147;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_id_manager ON reservations(id, manager_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_allocations_id_manager ON reservation_allocations(id, manager_id);

DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_reservations_customer_manager') THEN
  ALTER TABLE reservations ADD CONSTRAINT fk_reservations_customer_manager
    FOREIGN KEY(customer_id,manager_id) REFERENCES customers(id,manager_id) ON DELETE RESTRICT;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_reservations_station_manager') THEN
  ALTER TABLE reservations ADD CONSTRAINT fk_reservations_station_manager
    FOREIGN KEY(station_id,manager_id) REFERENCES stations(id,manager_id) ON DELETE RESTRICT;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_reservation_allocations_reservation_manager') THEN
  ALTER TABLE reservation_allocations ADD CONSTRAINT fk_reservation_allocations_reservation_manager
    FOREIGN KEY(reservation_id,manager_id) REFERENCES reservations(id,manager_id) ON DELETE CASCADE;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_reservation_allocations_station_manager') THEN
  ALTER TABLE reservation_allocations ADD CONSTRAINT fk_reservation_allocations_station_manager
    FOREIGN KEY(station_id,manager_id) REFERENCES stations(id,manager_id) ON DELETE CASCADE;
 END IF;
END $$;