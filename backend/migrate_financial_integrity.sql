-- GameNexa financial tenant-integrity hardening
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_reservations_id_manager') THEN
    ALTER TABLE reservations ADD CONSTRAINT uq_reservations_id_manager UNIQUE (id, manager_id);
  END IF;
END $$;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_wallet_customer_manager') THEN
    ALTER TABLE wallet_transactions ADD CONSTRAINT fk_wallet_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE CASCADE NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_gn_customer_manager') THEN
    ALTER TABLE gn_ledger ADD CONSTRAINT fk_gn_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE CASCADE NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_lp_customer_manager') THEN
    ALTER TABLE lp_ledger ADD CONSTRAINT fk_lp_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE CASCADE NOT VALID;
  END IF;
END $$;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_payment_customer_manager') THEN
    ALTER TABLE payment_transactions ADD CONSTRAINT fk_payment_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE CASCADE NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_payment_reservation_manager') THEN
    ALTER TABLE payment_transactions ADD CONSTRAINT fk_payment_reservation_manager FOREIGN KEY (reservation_id, manager_id) REFERENCES reservations(id, manager_id) ON DELETE CASCADE NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_invoices_reservation_manager') THEN
    ALTER TABLE invoices ADD CONSTRAINT fk_invoices_reservation_manager FOREIGN KEY (reservation_id, manager_id) REFERENCES reservations(id, manager_id) ON DELETE RESTRICT NOT VALID;
  END IF;
END $$;
ALTER TABLE wallet_transactions VALIDATE CONSTRAINT fk_wallet_customer_manager;
ALTER TABLE gn_ledger VALIDATE CONSTRAINT fk_gn_customer_manager;
ALTER TABLE lp_ledger VALIDATE CONSTRAINT fk_lp_customer_manager;
ALTER TABLE payment_transactions VALIDATE CONSTRAINT fk_payment_customer_manager;
ALTER TABLE payment_transactions VALIDATE CONSTRAINT fk_payment_reservation_manager;
ALTER TABLE invoices VALIDATE CONSTRAINT fk_invoices_reservation_manager;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='wallet_transactions_amount_nonnegative') THEN
    ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_amount_nonnegative CHECK (amount >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='gn_ledger_amount_nonnegative') THEN
    ALTER TABLE gn_ledger ADD CONSTRAINT gn_ledger_amount_nonnegative CHECK (amount >= 0);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='lp_ledger_amount_nonnegative') THEN
    ALTER TABLE lp_ledger ADD CONSTRAINT lp_ledger_amount_nonnegative CHECK (amount >= 0);
  END IF;
END $$;
