-- GameNexa canonical game/session/invoice foundation.
-- Additive only: no existing table/data is removed or altered.

CREATE TABLE IF NOT EXISTS game_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    station_id INT NOT NULL REFERENCES stations(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    console_type VARCHAR(50) NOT NULL,
    controller_count INT NOT NULL CHECK (controller_count BETWEEN 1 AND 4),
    started_at TIMESTAMPTZ NOT NULL,
    paused_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    -- Financial duration is exact to the second. duration_minutes is compatibility/display only.
    duration_minutes INT NOT NULL DEFAULT 0 CHECK (duration_minutes >= 0),
    duration_seconds BIGINT NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
    game_cost NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (game_cost >= 0),
    buffet_cost NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (buffet_cost >= 0),
    total_cost NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
    pricing_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX IF NOT EXISTS idx_game_sessions_manager_time
    ON game_sessions(manager_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_game_sessions_manager_station
    ON game_sessions(manager_id, station_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_game_sessions_id_manager
    ON game_sessions(id, manager_id);

CREATE TABLE IF NOT EXISTS session_participants (
    id BIGSERIAL PRIMARY KEY,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    customer_id INT REFERENCES customers(id) ON DELETE RESTRICT,
    participant_key VARCHAR(120) NOT NULL,
    participant_name VARCHAR(150) NOT NULL DEFAULT '',
    is_guest BOOLEAN NOT NULL DEFAULT FALSE,
    is_payer BOOLEAN NOT NULL DEFAULT TRUE,
    share_amount NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (share_amount >= 0),
    prepayment_amount NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (prepayment_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, participant_key),
    UNIQUE(id, manager_id),
    CHECK ((is_guest = TRUE AND customer_id IS NULL) OR (is_guest = FALSE AND customer_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_session_participants_manager_customer
    ON session_participants(manager_id, customer_id, created_at DESC);

DO $$
BEGIN
    ALTER TABLE session_participants ALTER COLUMN customer_id DROP NOT NULL;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='session_participants' AND column_name='participant_key') THEN
        ALTER TABLE session_participants ADD COLUMN participant_key VARCHAR(120);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='session_participants' AND column_name='participant_name') THEN
        ALTER TABLE session_participants ADD COLUMN participant_name VARCHAR(150) NOT NULL DEFAULT '';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='session_participants' AND column_name='is_guest') THEN
        ALTER TABLE session_participants ADD COLUMN is_guest BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

UPDATE session_participants
SET participant_key = COALESCE('customer:' || customer_id::text, 'legacy:' || id::text)
WHERE participant_key IS NULL;

ALTER TABLE session_participants ALTER COLUMN participant_key SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'session_participants_session_id_customer_id_key') THEN
        ALTER TABLE session_participants DROP CONSTRAINT session_participants_session_id_customer_id_key;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_participants_session_key
    ON session_participants(session_id, participant_key);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_session_participant_identity') THEN
        ALTER TABLE session_participants ADD CONSTRAINT ck_session_participant_identity
        CHECK ((is_guest = TRUE AND customer_id IS NULL) OR (is_guest = FALSE AND customer_id IS NOT NULL));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS session_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    product_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(30,10) NOT NULL CHECK (unit_price >= 0),
    target_customer_id INT REFERENCES customers(id) ON DELETE RESTRICT,
    line_total NUMERIC(30,10) NOT NULL CHECK (line_total >= 0),
    product_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_session_orders_manager_session
    ON session_orders(manager_id, session_id, created_at DESC);
CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number VARCHAR(80) NOT NULL,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE RESTRICT,
    station_id INT NOT NULL REFERENCES stations(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    currency VARCHAR(10) NOT NULL DEFAULT 'IRT',
    game_cost NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (game_cost >= 0),
    buffet_cost NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (buffet_cost >= 0),
    total_amount NUMERIC(30,10) NOT NULL CHECK (total_amount >= 0),
    paid_amount NUMERIC(30,10) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    items_snapshot JSONB NOT NULL DEFAULT '[]'::jsonb,
    pricing_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    customer_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    manager_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(manager_id, invoice_number),
    UNIQUE(session_id, customer_id)
);
CREATE INDEX IF NOT EXISTS idx_invoices_manager_customer
    ON invoices(manager_id, customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_manager_session
    ON invoices(manager_id, session_id);

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE RESTRICT;
CREATE INDEX IF NOT EXISTS idx_payment_transactions_manager_invoice
    ON payment_transactions(manager_id, invoice_id);

-- Defense-in-depth for cross-manager foreign-key relationships.
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_id_manager
    ON customers(id, manager_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_stations_id_manager
    ON stations(id, manager_id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_game_sessions_station_manager') THEN
        ALTER TABLE game_sessions ADD CONSTRAINT fk_game_sessions_station_manager
            FOREIGN KEY (station_id, manager_id) REFERENCES stations(id, manager_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_session_participants_customer_manager') THEN
        ALTER TABLE session_participants ADD CONSTRAINT fk_session_participants_customer_manager
            FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_session_participants_session_manager') THEN
        ALTER TABLE session_participants ADD CONSTRAINT fk_session_participants_session_manager
            FOREIGN KEY (session_id, manager_id) REFERENCES game_sessions(id, manager_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_session_orders_session_manager') THEN
        ALTER TABLE session_orders ADD CONSTRAINT fk_session_orders_session_manager
            FOREIGN KEY (session_id, manager_id) REFERENCES game_sessions(id, manager_id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_session_orders_customer_manager') THEN
        ALTER TABLE session_orders ADD CONSTRAINT fk_session_orders_customer_manager
            FOREIGN KEY (target_customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_customer_manager') THEN
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_customer_manager
            FOREIGN KEY (customer_id, manager_id) REFERENCES customers(id, manager_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_session_manager') THEN
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_session_manager
            FOREIGN KEY (session_id, manager_id) REFERENCES game_sessions(id, manager_id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_station_manager') THEN
        ALTER TABLE invoices ADD CONSTRAINT fk_invoices_station_manager
            FOREIGN KEY (station_id, manager_id) REFERENCES stations(id, manager_id) ON DELETE RESTRICT;
    END IF;
END $$;-- Event log: only lifecycle/business changes are stored; no per-second telemetry.
CREATE TABLE IF NOT EXISTS session_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    event_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    sequence_no BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(manager_id, event_id),
    UNIQUE(session_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS idx_session_events_manager_session
    ON session_events(manager_id, session_id, sequence_no);-- A customer may belong to many historical sessions, but only one active session
-- at a time. This claim table makes that invariant database-enforceable.
CREATE TABLE IF NOT EXISTS active_session_customer_claims (
    customer_id INT PRIMARY KEY REFERENCES customers(id) ON DELETE CASCADE,
    manager_id VARCHAR(50) NOT NULL REFERENCES managers(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(session_id, customer_id)
);
CREATE INDEX IF NOT EXISTS idx_active_session_claims_manager_session
    ON active_session_customer_claims(manager_id, session_id);-- Releasing claims is explicit and transactional at session completion.
CREATE OR REPLACE FUNCTION release_session_customer_claims()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('SETTLED','CANCELLED','CLOSED') AND
       OLD.status IS DISTINCT FROM NEW.status THEN
        DELETE FROM active_session_customer_claims
        WHERE session_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;DROP TRIGGER IF EXISTS trg_release_session_customer_claims ON game_sessions;
CREATE TRIGGER trg_release_session_customer_claims
AFTER UPDATE OF status ON game_sessions
FOR EACH ROW EXECUTE FUNCTION release_session_customer_claims();