ALTER TABLE payment_transactions ADD COLUMN provider character varying(50);
ALTER TABLE payment_transactions ADD COLUMN gateway_transaction_id character varying(100);
ALTER TABLE payment_transactions ADD COLUMN authority character varying(100);
ALTER TABLE payment_transactions ADD COLUMN currency character varying(10) DEFAULT 'IRT';
ALTER TABLE payment_transactions ADD COLUMN verified_at timestamp with time zone;
