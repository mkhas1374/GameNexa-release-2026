ALTER TABLE customers ADD COLUMN IF NOT EXISTS debt NUMERIC(30,10) NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS credit NUMERIC(30,10) NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS invite_code VARCHAR(80);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS invited_by_code VARCHAR(80);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pending_gn INT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS total_qualified_spend NUMERIC(30,10) NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS total_visits_count INT NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS last_tier_review_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS invite_points_awarded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS rewards_consumed INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_manager_invite_code ON customers(manager_id, invite_code) WHERE invite_code IS NOT NULL AND invite_code <> '';
CREATE TABLE IF NOT EXISTS customer_point_logs (
  id BIGSERIAL PRIMARY KEY,
  manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
  customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  points BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customer_point_logs_manager_customer ON customer_point_logs(manager_id, customer_id, created_at DESC);
CREATE TABLE IF NOT EXISTS customer_transactions (
  id BIGSERIAL PRIMARY KEY,
  manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
  customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  customer_name VARCHAR(150) NOT NULL DEFAULT '',
  station_name VARCHAR(150) NOT NULL DEFAULT '',
  title VARCHAR(255) NOT NULL DEFAULT '',
  amount NUMERIC(30,10) NOT NULL DEFAULT 0,
  paid_amount NUMERIC(30,10) NOT NULL DEFAULT 0,
  status VARCHAR(40) NOT NULL DEFAULT 'UNREVIEWED',
  date_str VARCHAR(30) NOT NULL DEFAULT '', time_str VARCHAR(30) NOT NULL DEFAULT '',
  segment_details TEXT NOT NULL DEFAULT '', buffet_details TEXT NOT NULL DEFAULT '',
  event_timestamp BIGINT NOT NULL DEFAULT 0, play_minutes INT NOT NULL DEFAULT 0,
  game_cost NUMERIC(30,10) NOT NULL DEFAULT 0, food_cost NUMERIC(30,10) NOT NULL DEFAULT 0,
  local_id BIGINT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_customer_transactions_manager_customer ON customer_transactions(manager_id,customer_id,created_at DESC);
