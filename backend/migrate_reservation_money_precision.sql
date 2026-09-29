BEGIN;
ALTER TABLE reservations
  ALTER COLUMN snap_base_price TYPE NUMERIC(30,10) USING snap_base_price::numeric,
  ALTER COLUMN snap_discount_amount TYPE NUMERIC(30,10) USING snap_discount_amount::numeric,
  ALTER COLUMN snap_final_price TYPE NUMERIC(30,10) USING snap_final_price::numeric,
  ALTER COLUMN snap_payable_amount TYPE NUMERIC(30,10) USING snap_payable_amount::numeric,
  ALTER COLUMN snap_deposit_amount TYPE NUMERIC(30,10) USING snap_deposit_amount::numeric;
COMMIT;
