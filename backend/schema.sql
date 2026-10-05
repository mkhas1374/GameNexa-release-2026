--
-- PostgreSQL database dump
--

\restrict YZGvcabeo1NJx3Qiya5N2X4TEeVUrEqdvF7jEYOWnTOE7HyGdAyUp4cvkB3aSk4

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: release_session_customer_claims(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.release_session_customer_claims() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status IN ('SETTLED','CANCELLED','CLOSED') AND
       OLD.status IS DISTINCT FROM NEW.status THEN
        DELETE FROM active_session_customer_claims
        WHERE session_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: active_session_customer_claims; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.active_session_customer_claims (
    customer_id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    session_id uuid NOT NULL,
    claimed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: api_idempotency_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.api_idempotency_keys (
    idempotency_key character varying(100) NOT NULL,
    manager_id character varying(50) NOT NULL,
    scope character varying(50) NOT NULL,
    response_body jsonb,
    response_status integer,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: configuration_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.configuration_revisions (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    version_number integer NOT NULL,
    settings jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: configuration_revisions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.configuration_revisions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: configuration_revisions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.configuration_revisions_id_seq OWNED BY public.configuration_revisions.id;


--
-- Name: customer_point_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_point_logs (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    title character varying(255) NOT NULL,
    points bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: customer_point_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_point_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_point_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_point_logs_id_seq OWNED BY public.customer_point_logs.id;


--
-- Name: customer_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_transactions (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer,
    customer_name character varying(150) DEFAULT ''::character varying NOT NULL,
    station_name character varying(150) DEFAULT ''::character varying NOT NULL,
    title character varying(255) DEFAULT ''::character varying NOT NULL,
    amount numeric(30,10) DEFAULT 0 NOT NULL,
    paid_amount numeric(30,10) DEFAULT 0 NOT NULL,
    status character varying(40) DEFAULT 'UNREVIEWED'::character varying NOT NULL,
    date_str character varying(30) DEFAULT ''::character varying NOT NULL,
    time_str character varying(30) DEFAULT ''::character varying NOT NULL,
    segment_details text DEFAULT ''::text NOT NULL,
    buffet_details text DEFAULT ''::text NOT NULL,
    event_timestamp bigint DEFAULT 0 NOT NULL,
    play_minutes integer DEFAULT 0 NOT NULL,
    game_cost numeric(30,10) DEFAULT 0 NOT NULL,
    food_cost numeric(30,10) DEFAULT 0 NOT NULL,
    local_id bigint,
    session_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: customer_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_transactions_id_seq OWNED BY public.customer_transactions.id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    phone_number character varying(20) NOT NULL,
    full_name character varying(150),
    club_tier character varying(50) DEFAULT 'BRONZE'::character varying,
    wallet_balance numeric(15,2) DEFAULT 0.00,
    gn_balance integer DEFAULT 0,
    lp_balance integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    restricted_until timestamp with time zone,
    pending_surcharge_percent numeric(5,2) DEFAULT 0,
    password_hash character varying(255),
    token_version integer NOT NULL DEFAULT 1,
    debt numeric(30,10) DEFAULT 0 NOT NULL,
    credit numeric(30,10) DEFAULT 0 NOT NULL,
    description text DEFAULT ''::text NOT NULL,
    invite_code character varying(80),
    invited_by_code character varying(80),
    pending_gn integer DEFAULT 0 NOT NULL,
    last_activity_at timestamp with time zone,
    total_qualified_spend numeric(30,10) DEFAULT 0 NOT NULL,
    total_visits_count integer DEFAULT 0 NOT NULL,
    last_tier_review_at timestamp with time zone,
    invite_points_awarded boolean DEFAULT false NOT NULL,
    rewards_consumed integer DEFAULT 0 NOT NULL
);


--
-- Name: customers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customers_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;


--
-- Name: financial_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financial_audit_logs (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer,
    invoice_id uuid,
    reservation_id integer,
    payment_id integer,
    event_type character varying(60) NOT NULL,
    amount numeric(30,10),
    currency character varying(10),
    actor character varying(100) NOT NULL,
    idempotency_key character varying(128),
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: financial_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.financial_audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: financial_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.financial_audit_logs_id_seq OWNED BY public.financial_audit_logs.id;


--
-- Name: game_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.game_sessions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    manager_id character varying(50) NOT NULL,
    station_id integer NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    console_type character varying(50) NOT NULL,
    controller_count integer NOT NULL,
    started_at timestamp with time zone NOT NULL,
    paused_at timestamp with time zone,
    ended_at timestamp with time zone,
    duration_minutes integer DEFAULT 0 NOT NULL,
    duration_seconds bigint DEFAULT 0 NOT NULL,
    game_cost numeric(30,10) DEFAULT 0 NOT NULL,
    buffet_cost numeric(30,10) DEFAULT 0 NOT NULL,
    total_cost numeric(30,10) DEFAULT 0 NOT NULL,
    pricing_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT game_sessions_buffet_cost_check CHECK ((buffet_cost >= (0)::numeric)),
    CONSTRAINT game_sessions_check CHECK (((ended_at IS NULL) OR (ended_at >= started_at))),
    CONSTRAINT game_sessions_controller_count_check CHECK (((controller_count >= 1) AND (controller_count <= 4))),
    CONSTRAINT game_sessions_duration_minutes_check CHECK ((duration_minutes >= 0)),
    CONSTRAINT game_sessions_duration_seconds_check CHECK ((duration_seconds >= 0)),
    CONSTRAINT game_sessions_game_cost_check CHECK ((game_cost >= (0)::numeric)),
    CONSTRAINT game_sessions_total_cost_check CHECK ((total_cost >= (0)::numeric))
);


--
-- Name: gn_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gn_ledger (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    amount integer NOT NULL,
    type character varying(50) NOT NULL,
    reference_type character varying(50),
    reference_id character varying(100),
    idempotency_key character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT gn_ledger_amount_nonnegative CHECK ((amount >= 0)),
    CONSTRAINT gn_ledger_type_check CHECK (((type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);


--
-- Name: gn_ledger_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gn_ledger_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: gn_ledger_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.gn_ledger_id_seq OWNED BY public.gn_ledger.id;


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    invoice_number character varying(80) NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer,
    session_id uuid,
    station_id integer,
    status character varying(30) DEFAULT 'UNPAID'::character varying NOT NULL,
    currency character varying(10) DEFAULT 'IRT'::character varying NOT NULL,
    game_cost numeric(30,10) DEFAULT 0 NOT NULL,
    buffet_cost numeric(30,10) DEFAULT 0 NOT NULL,
    total_amount numeric(30,10) NOT NULL,
    paid_amount numeric(30,10) DEFAULT 0 NOT NULL,
    items_snapshot jsonb DEFAULT '[]'::jsonb NOT NULL,
    pricing_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    customer_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    manager_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    settled_at timestamp with time zone,
    settlement_idempotency_key character varying(100),
    reservation_id integer,
    CONSTRAINT invoices_buffet_cost_check CHECK ((buffet_cost >= (0)::numeric)),
    CONSTRAINT invoices_game_cost_check CHECK ((game_cost >= (0)::numeric)),
    CONSTRAINT invoices_paid_amount_check CHECK ((paid_amount >= (0)::numeric)),
    CONSTRAINT invoices_total_amount_check CHECK ((total_amount >= (0)::numeric))
);


--
-- Name: lp_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lp_ledger (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    amount integer NOT NULL,
    type character varying(50) NOT NULL,
    reference_type character varying(50),
    reference_id character varying(100),
    idempotency_key character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT lp_ledger_amount_nonnegative CHECK ((amount >= 0)),
    CONSTRAINT lp_ledger_type_check CHECK (((type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);


--
-- Name: lp_ledger_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lp_ledger_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lp_ledger_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lp_ledger_id_seq OWNED BY public.lp_ledger.id;


--
-- Name: manager_device_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_device_bindings (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    device_id character varying(255) NOT NULL,
    first_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: manager_device_bindings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_device_bindings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_device_bindings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_device_bindings_id_seq OWNED BY public.manager_device_bindings.id;


--
-- Name: manager_entitlements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_entitlements (
    id bigint NOT NULL,
    manager_id character varying NOT NULL,
    entitlement_type character varying NOT NULL,
    plan_id character varying NOT NULL,
    status character varying DEFAULT 'ACTIVE'::character varying NOT NULL,
    starts_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    source character varying DEFAULT 'SYSTEM'::character varying NOT NULL,
    max_stations integer,
    max_customers integer,
    allowed_console_type character varying,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    max_devices integer DEFAULT 1 NOT NULL,
    CONSTRAINT manager_entitlements_check CHECK ((expires_at > starts_at)),
    CONSTRAINT manager_entitlements_entitlement_type_check CHECK (((entitlement_type)::text = ANY ((ARRAY['TRIAL'::character varying, 'SUBSCRIPTION'::character varying, 'SUPER_MANAGER_LIFETIME'::character varying])::text[]))),
    CONSTRAINT manager_entitlements_max_customers_check CHECK (((max_customers IS NULL) OR (max_customers > 0))),
    CONSTRAINT manager_entitlements_max_devices_chk CHECK (((max_devices >= 1) AND (max_devices <= 100))),
    CONSTRAINT manager_entitlements_max_stations_check CHECK (((max_stations IS NULL) OR (max_stations > 0))),
    CONSTRAINT manager_entitlements_plan_id_check CHECK (((plan_id)::text = ANY (ARRAY['TRIAL_24H'::text, 'MONTHLY'::text, '1_MONTH'::text, 'THREE_MONTHS'::text, 'YEARLY'::text, '3_MONTHS'::text, '12_MONTHS'::text, 'SUPER_MANAGER_LIFETIME'::text]))),
    CONSTRAINT manager_entitlements_source_check CHECK (((source)::text = ANY ((ARRAY['SYSTEM'::character varying, 'TRIAL'::character varying, 'SUBSCRIPTION_PAYMENT'::character varying, 'SUPER_MANAGER'::character varying])::text[]))),
    CONSTRAINT manager_entitlements_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: manager_entitlements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_entitlements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_entitlements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_entitlements_id_seq OWNED BY public.manager_entitlements.id;


--
-- Name: manager_payment_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manager_payment_methods (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    method_code character varying(50) NOT NULL,
    display_name character varying(120) NOT NULL,
    instructions text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: manager_payment_methods_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manager_payment_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manager_payment_methods_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manager_payment_methods_id_seq OWNED BY public.manager_payment_methods.id;


--
-- Name: managers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.managers (
    id character varying(50) NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    token_version integer NOT NULL DEFAULT 1,
    token character varying(255),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    role character varying(50) DEFAULT 'MANAGER'::character varying,
    display_name character varying(255),
    gamenet_name character varying(255),
    phone character varying(50),
    plan_type character varying(50),
    subscription_status character varying(50) DEFAULT 'ACTIVE'::character varying,
    payment_status character varying(50) DEFAULT 'PAID'::character varying
);


--
-- Name: manual_payment_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.manual_payment_requests (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    reservation_id integer,
    invoice_id uuid,
    purpose character varying(40) NOT NULL,
    amount numeric(30,10),
    currency character varying(10) DEFAULT 'IRT'::character varying NOT NULL,
    payment_method_id bigint,
    payment_method_code character varying(50),
    payment_reference character varying(255),
    receipt_reference character varying(255),
    customer_note text,
    manager_note text,
    status character varying(40) DEFAULT 'PENDING_MANAGER_REVIEW'::character varying NOT NULL,
    rejection_reason text,
    reviewed_by character varying(50),
    reviewed_at timestamp with time zone,
    idempotency_key character varying(128) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    approved_amount numeric(30,10),
    approved_currency character varying(10),
    approved_payment_method_code character varying(50),
    CONSTRAINT manual_payment_requests_purpose_check CHECK (((purpose)::text = ANY ((ARRAY['WALLET_TOPUP'::character varying, 'RESERVATION_PAYMENT'::character varying, 'MANUAL_GN'::character varying, 'MANUAL_LP'::character varying])::text[])))
);


--
-- Name: manual_payment_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.manual_payment_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: manual_payment_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.manual_payment_requests_id_seq OWNED BY public.manual_payment_requests.id;


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    title character varying(200) NOT NULL,
    message text NOT NULL,
    is_read boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;


--
-- Name: payment_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_transactions (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    reservation_id integer,
    amount numeric(15,2) NOT NULL,
    status character varying(50) NOT NULL,
    idempotency_key character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    provider character varying(50),
    gateway_transaction_id character varying(100),
    authority character varying(100),
    currency character varying(10) DEFAULT 'IRT'::character varying,
    verified_at timestamp with time zone,
    invoice_id uuid,
    session_id uuid
);


--
-- Name: payment_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payment_transactions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payment_transactions_id_seq OWNED BY public.payment_transactions.id;


--
-- Name: reservation_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_allocations (
    id integer NOT NULL,
    reservation_id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    station_id integer NOT NULL
);


--
-- Name: reservation_allocations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservation_allocations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservation_allocations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservation_allocations_id_seq OWNED BY public.reservation_allocations.id;


--
-- Name: reservation_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_audit_logs (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    reservation_id integer NOT NULL,
    actor character varying(100) NOT NULL,
    old_status character varying(50),
    new_status character varying(50),
    reason text,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: reservation_audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservation_audit_logs_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservation_audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservation_audit_logs_id_seq OWNED BY public.reservation_audit_logs.id;


--
-- Name: reservation_request_idempotency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservation_request_idempotency (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    reservation_ids jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: reservation_request_idempotency_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservation_request_idempotency_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservation_request_idempotency_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservation_request_idempotency_id_seq OWNED BY public.reservation_request_idempotency.id;


--
-- Name: reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reservations (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    station_id integer,
    type character varying(50) NOT NULL,
    status character varying(50) NOT NULL,
    start_time timestamp with time zone NOT NULL,
    end_time timestamp with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    config_revision_id integer,
    snap_base_price numeric(30,10) NOT NULL,
    snap_discount_amount numeric(30,10) DEFAULT 0.00,
    snap_final_price numeric(30,10) NOT NULL,
    snap_payable_amount numeric(30,10) NOT NULL,
    snap_deposit_amount numeric(30,10) DEFAULT 0.00,
    snap_currency character varying(10) DEFAULT 'IRT'::character varying,
    snap_pricing_version integer,
    snap_cancellation_policy jsonb,
    snap_gn_policy jsonb,
    snap_lp_policy jsonb,
    snap_wallet_policy jsonb,
    snap_restriction jsonb,
    snap_surcharge jsonb,
    snap_vip_policy jsonb,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT reservations_type_check CHECK (((type)::text = ANY ((ARRAY['NORMAL_RESERVATION'::character varying, 'FULL_HALL'::character varying, 'EXCLUSIVE_FULL_DAY'::character varying])::text[])))
);


--
-- Name: reservations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reservations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reservations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reservations_id_seq OWNED BY public.reservations.id;


--
-- Name: session_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_events (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    manager_id character varying(50) NOT NULL,
    session_id uuid NOT NULL,
    event_id character varying(120) NOT NULL,
    event_type character varying(30) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    sequence_no bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: session_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_orders (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    manager_id character varying(50) NOT NULL,
    session_id uuid NOT NULL,
    product_name character varying(150) NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(30,10) NOT NULL,
    target_customer_id integer,
    line_total numeric(30,10) NOT NULL,
    product_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT session_orders_line_total_check CHECK ((line_total >= (0)::numeric)),
    CONSTRAINT session_orders_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT session_orders_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: session_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_participants (
    id bigint NOT NULL,
    manager_id character varying(50) NOT NULL,
    session_id uuid NOT NULL,
    customer_id integer,
    is_payer boolean DEFAULT true NOT NULL,
    share_amount numeric(30,10) DEFAULT 0 NOT NULL,
    prepayment_amount numeric(30,10) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    participant_key character varying(120) NOT NULL,
    participant_name character varying(150) DEFAULT ''::character varying NOT NULL,
    is_guest boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_session_participant_identity CHECK ((((is_guest = true) AND (customer_id IS NULL)) OR ((is_guest = false) AND (customer_id IS NOT NULL)))),
    CONSTRAINT session_participants_prepayment_amount_check CHECK ((prepayment_amount >= (0)::numeric)),
    CONSTRAINT session_participants_share_amount_check CHECK ((share_amount >= (0)::numeric))
);


--
-- Name: session_participants_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.session_participants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: session_participants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.session_participants_id_seq OWNED BY public.session_participants.id;


--
-- Name: stations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stations (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    active boolean DEFAULT true,
    reservable boolean DEFAULT true,
    controller_capacity integer DEFAULT 1,
    console_type character varying(50) DEFAULT 'PS4'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: stations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stations_id_seq OWNED BY public.stations.id;


--
-- Name: subscription_payment_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_payment_requests (
    id integer NOT NULL,
    manager_id character varying(50),
    plan_id character varying(50) NOT NULL,
    amount numeric(15,2) NOT NULL,
    payment_reference character varying(255),
    receipt_reference character varying(255),
    customer_note text,
    status character varying(50) DEFAULT 'PENDING'::character varying,
    rejection_reason text,
    reviewed_at timestamp with time zone,
    reviewed_by character varying(50),
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    buyer_phone character varying(50),
    buyer_name character varying(200),
    buyer_gamenet_name character varying(200),
    buyer_device_id character varying(255),
    provisioned_account boolean DEFAULT false NOT NULL,
    activation_secret_hash text,
    idempotency_key character varying(128),
    CONSTRAINT subscription_payment_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: subscription_payment_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.subscription_payment_requests_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subscription_payment_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.subscription_payment_requests_id_seq OWNED BY public.subscription_payment_requests.id;


--
-- Name: subscription_store_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_store_config (
    id smallint NOT NULL,
    version_number integer DEFAULT 1 NOT NULL,
    settings jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by character varying(50),
    CONSTRAINT subscription_store_config_id_check CHECK ((id = 1))
);


--
-- Name: trial_device_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_device_audit_log (
    id bigint NOT NULL,
    device_id character varying(255),
    device_fingerprint character varying(255),
    action character varying(30) NOT NULL,
    actor_manager_id character varying(50),
    previous_started_at timestamp with time zone,
    previous_expires_at timestamp with time zone,
    new_started_at timestamp with time zone,
    new_expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trial_device_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trial_device_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trial_device_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trial_device_audit_log_id_seq OWNED BY public.trial_device_audit_log.id;


--
-- Name: trial_device_blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_device_blocks (
    id bigint NOT NULL,
    device_id character varying(255),
    device_fingerprint character varying(255),
    reason character varying(50) DEFAULT 'DELETED_BY_SUPER_MANAGER'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT trial_device_blocks_identity_chk CHECK (((device_id IS NOT NULL) OR (device_fingerprint IS NOT NULL)))
);


--
-- Name: trial_device_blocks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trial_device_blocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trial_device_blocks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trial_device_blocks_id_seq OWNED BY public.trial_device_blocks.id;


--
-- Name: trial_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trial_devices (
    device_id character varying(255) NOT NULL,
    device_name character varying(255),
    started_at timestamp with time zone DEFAULT now(),
    expires_at timestamp with time zone,
    status character varying(50) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    manager_id character varying,
    device_fingerprint character varying(255)
);


--
-- Name: wallet_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_transactions (
    id integer NOT NULL,
    manager_id character varying(50) NOT NULL,
    customer_id integer NOT NULL,
    amount numeric(15,2) NOT NULL,
    type character varying(50) NOT NULL,
    reference_type character varying(50),
    reference_id character varying(100),
    idempotency_key character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT wallet_transactions_amount_nonnegative CHECK ((amount >= (0)::numeric)),
    CONSTRAINT wallet_transactions_type_check CHECK (((type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);


--
-- Name: wallet_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.wallet_transactions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: wallet_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.wallet_transactions_id_seq OWNED BY public.wallet_transactions.id;


--
-- Name: configuration_revisions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration_revisions ALTER COLUMN id SET DEFAULT nextval('public.configuration_revisions_id_seq'::regclass);


--
-- Name: customer_point_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_point_logs ALTER COLUMN id SET DEFAULT nextval('public.customer_point_logs_id_seq'::regclass);


--
-- Name: customer_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_transactions ALTER COLUMN id SET DEFAULT nextval('public.customer_transactions_id_seq'::regclass);


--
-- Name: customers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);


--
-- Name: financial_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.financial_audit_logs_id_seq'::regclass);


--
-- Name: gn_ledger id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger ALTER COLUMN id SET DEFAULT nextval('public.gn_ledger_id_seq'::regclass);


--
-- Name: lp_ledger id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger ALTER COLUMN id SET DEFAULT nextval('public.lp_ledger_id_seq'::regclass);


--
-- Name: manager_device_bindings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_device_bindings ALTER COLUMN id SET DEFAULT nextval('public.manager_device_bindings_id_seq'::regclass);


--
-- Name: manager_entitlements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_entitlements ALTER COLUMN id SET DEFAULT nextval('public.manager_entitlements_id_seq'::regclass);


--
-- Name: manager_payment_methods id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_payment_methods ALTER COLUMN id SET DEFAULT nextval('public.manager_payment_methods_id_seq'::regclass);


--
-- Name: manual_payment_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests ALTER COLUMN id SET DEFAULT nextval('public.manual_payment_requests_id_seq'::regclass);


--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);


--
-- Name: payment_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions ALTER COLUMN id SET DEFAULT nextval('public.payment_transactions_id_seq'::regclass);


--
-- Name: reservation_allocations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations ALTER COLUMN id SET DEFAULT nextval('public.reservation_allocations_id_seq'::regclass);


--
-- Name: reservation_audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_audit_logs ALTER COLUMN id SET DEFAULT nextval('public.reservation_audit_logs_id_seq'::regclass);


--
-- Name: reservation_request_idempotency id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_request_idempotency ALTER COLUMN id SET DEFAULT nextval('public.reservation_request_idempotency_id_seq'::regclass);


--
-- Name: reservations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations ALTER COLUMN id SET DEFAULT nextval('public.reservations_id_seq'::regclass);


--
-- Name: session_participants id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants ALTER COLUMN id SET DEFAULT nextval('public.session_participants_id_seq'::regclass);


--
-- Name: stations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stations ALTER COLUMN id SET DEFAULT nextval('public.stations_id_seq'::regclass);


--
-- Name: subscription_payment_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_payment_requests ALTER COLUMN id SET DEFAULT nextval('public.subscription_payment_requests_id_seq'::regclass);


--
-- Name: trial_device_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_device_audit_log ALTER COLUMN id SET DEFAULT nextval('public.trial_device_audit_log_id_seq'::regclass);


--
-- Name: trial_device_blocks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_device_blocks ALTER COLUMN id SET DEFAULT nextval('public.trial_device_blocks_id_seq'::regclass);


--
-- Name: wallet_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions ALTER COLUMN id SET DEFAULT nextval('public.wallet_transactions_id_seq'::regclass);


--
-- Name: active_session_customer_claims active_session_customer_claims_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.active_session_customer_claims
    ADD CONSTRAINT active_session_customer_claims_pkey PRIMARY KEY (customer_id);


--
-- Name: active_session_customer_claims active_session_customer_claims_session_id_customer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.active_session_customer_claims
    ADD CONSTRAINT active_session_customer_claims_session_id_customer_id_key UNIQUE (session_id, customer_id);


--
-- Name: api_idempotency_keys api_idempotency_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_idempotency_keys
    ADD CONSTRAINT api_idempotency_keys_pkey PRIMARY KEY (idempotency_key);


--
-- Name: configuration_revisions configuration_revisions_manager_id_version_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration_revisions
    ADD CONSTRAINT configuration_revisions_manager_id_version_number_key UNIQUE (manager_id, version_number);


--
-- Name: configuration_revisions configuration_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration_revisions
    ADD CONSTRAINT configuration_revisions_pkey PRIMARY KEY (id);


--
-- Name: customer_point_logs customer_point_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_point_logs
    ADD CONSTRAINT customer_point_logs_pkey PRIMARY KEY (id);


--
-- Name: customer_transactions customer_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_transactions
    ADD CONSTRAINT customer_transactions_pkey PRIMARY KEY (id);


--
-- Name: customers customers_manager_id_phone_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_manager_id_phone_number_key UNIQUE (manager_id, phone_number);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: financial_audit_logs financial_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: game_sessions game_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_sessions
    ADD CONSTRAINT game_sessions_pkey PRIMARY KEY (id);


--
-- Name: gn_ledger gn_ledger_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger
    ADD CONSTRAINT gn_ledger_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: gn_ledger gn_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger
    ADD CONSTRAINT gn_ledger_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_manager_id_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_manager_id_invoice_number_key UNIQUE (manager_id, invoice_number);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_session_id_customer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_session_id_customer_id_key UNIQUE (session_id, customer_id);


--
-- Name: lp_ledger lp_ledger_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger
    ADD CONSTRAINT lp_ledger_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: lp_ledger lp_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger
    ADD CONSTRAINT lp_ledger_pkey PRIMARY KEY (id);


--
-- Name: manager_device_bindings manager_device_bindings_manager_id_device_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_device_bindings
    ADD CONSTRAINT manager_device_bindings_manager_id_device_id_key UNIQUE (manager_id, device_id);


--
-- Name: manager_device_bindings manager_device_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_device_bindings
    ADD CONSTRAINT manager_device_bindings_pkey PRIMARY KEY (id);


--
-- Name: manager_entitlements manager_entitlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_entitlements
    ADD CONSTRAINT manager_entitlements_pkey PRIMARY KEY (id);


--
-- Name: manager_payment_methods manager_payment_methods_manager_id_method_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_payment_methods
    ADD CONSTRAINT manager_payment_methods_manager_id_method_code_key UNIQUE (manager_id, method_code);


--
-- Name: manager_payment_methods manager_payment_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_payment_methods
    ADD CONSTRAINT manager_payment_methods_pkey PRIMARY KEY (id);


--
-- Name: managers managers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.managers
    ADD CONSTRAINT managers_pkey PRIMARY KEY (id);


--
-- Name: managers managers_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.managers
    ADD CONSTRAINT managers_username_key UNIQUE (username);


--
-- Name: manual_payment_requests manual_payment_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_pkey PRIMARY KEY (id);


--
-- Name: reservations no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT no_overlap EXCLUDE USING gist (manager_id WITH =, station_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (((status)::text <> ALL ((ARRAY['CANCELLED'::character varying, 'EXPIRED'::character varying, 'NO_SHOW'::character varying, 'REJECTED'::character varying, 'SUPERSEDED_BY_VIP'::character varying, 'SUPERSEDED_BY_VIP_PRIORITY'::character varying, 'VIP_PENDING_PAYMENT'::character varying, 'VIP_PAYMENT_PAID'::character varying])::text[])));


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: payment_transactions payment_transactions_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: payment_transactions payment_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_pkey PRIMARY KEY (id);


--
-- Name: reservation_allocations reservation_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT reservation_allocations_pkey PRIMARY KEY (id);


--
-- Name: reservation_allocations reservation_allocations_reservation_id_station_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT reservation_allocations_reservation_id_station_id_key UNIQUE (reservation_id, station_id);


--
-- Name: reservation_audit_logs reservation_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_audit_logs
    ADD CONSTRAINT reservation_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: reservation_request_idempotency reservation_request_idempotency_manager_id_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_request_idempotency
    ADD CONSTRAINT reservation_request_idempotency_manager_id_idempotency_key_key UNIQUE (manager_id, idempotency_key);


--
-- Name: reservation_request_idempotency reservation_request_idempotency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_request_idempotency
    ADD CONSTRAINT reservation_request_idempotency_pkey PRIMARY KEY (id);


--
-- Name: reservations reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (id);


--
-- Name: session_events session_events_manager_id_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_events
    ADD CONSTRAINT session_events_manager_id_event_id_key UNIQUE (manager_id, event_id);


--
-- Name: session_events session_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_events
    ADD CONSTRAINT session_events_pkey PRIMARY KEY (id);


--
-- Name: session_events session_events_session_id_sequence_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_events
    ADD CONSTRAINT session_events_session_id_sequence_no_key UNIQUE (session_id, sequence_no);


--
-- Name: session_orders session_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT session_orders_pkey PRIMARY KEY (id);


--
-- Name: session_participants session_participants_id_manager_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_id_manager_id_key UNIQUE (id, manager_id);


--
-- Name: session_participants session_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_pkey PRIMARY KEY (id);


--
-- Name: stations stations_manager_id_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stations
    ADD CONSTRAINT stations_manager_id_name_key UNIQUE (manager_id, name);


--
-- Name: stations stations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stations
    ADD CONSTRAINT stations_pkey PRIMARY KEY (id);


--
-- Name: subscription_payment_requests subscription_payment_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_payment_requests
    ADD CONSTRAINT subscription_payment_requests_pkey PRIMARY KEY (id);


--
-- Name: subscription_store_config subscription_store_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_store_config
    ADD CONSTRAINT subscription_store_config_pkey PRIMARY KEY (id);


--
-- Name: trial_device_audit_log trial_device_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_device_audit_log
    ADD CONSTRAINT trial_device_audit_log_pkey PRIMARY KEY (id);


--
-- Name: trial_device_blocks trial_device_blocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_device_blocks
    ADD CONSTRAINT trial_device_blocks_pkey PRIMARY KEY (id);


--
-- Name: trial_devices trial_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_devices
    ADD CONSTRAINT trial_devices_pkey PRIMARY KEY (device_id);


--
-- Name: reservations uq_reservations_id_manager; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT uq_reservations_id_manager UNIQUE (id, manager_id);


--
-- Name: wallet_transactions wallet_transactions_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: wallet_transactions wallet_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_pkey PRIMARY KEY (id);


--
-- Name: financial_audit_idempotency_key_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX financial_audit_idempotency_key_key ON public.financial_audit_logs USING btree (manager_id, event_type, idempotency_key);


--
-- Name: idx_active_session_claims_manager_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_active_session_claims_manager_session ON public.active_session_customer_claims USING btree (manager_id, session_id);


--
-- Name: idx_customer_point_logs_manager_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_point_logs_manager_customer ON public.customer_point_logs USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_customer_transactions_manager_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_transactions_manager_customer ON public.customer_transactions USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_financial_audit_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_financial_audit_invoice ON public.financial_audit_logs USING btree (manager_id, invoice_id);


--
-- Name: idx_financial_audit_manager_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_financial_audit_manager_created ON public.financial_audit_logs USING btree (manager_id, created_at DESC);


--
-- Name: idx_financial_audit_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_financial_audit_reservation ON public.financial_audit_logs USING btree (manager_id, reservation_id);


--
-- Name: idx_game_sessions_manager_station; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_game_sessions_manager_station ON public.game_sessions USING btree (manager_id, station_id, status);


--
-- Name: idx_game_sessions_manager_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_game_sessions_manager_time ON public.game_sessions USING btree (manager_id, started_at DESC);


--
-- Name: idx_invoices_manager_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoices_manager_customer ON public.invoices USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_invoices_manager_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoices_manager_reservation ON public.invoices USING btree (manager_id, reservation_id);


--
-- Name: idx_invoices_manager_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoices_manager_session ON public.invoices USING btree (manager_id, session_id);


--
-- Name: idx_manager_device_bindings_manager_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manager_device_bindings_manager_active ON public.manager_device_bindings USING btree (manager_id, active);


--
-- Name: idx_manager_payment_methods_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manager_payment_methods_active ON public.manager_payment_methods USING btree (manager_id, active);


--
-- Name: idx_manual_payment_requests_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_customer ON public.manual_payment_requests USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_manual_payment_requests_customer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_customer_status ON public.manual_payment_requests USING btree (manager_id, customer_id, status, created_at DESC);


--
-- Name: idx_manual_payment_requests_manager_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_manager_status ON public.manual_payment_requests USING btree (manager_id, status, created_at DESC);


--
-- Name: idx_manual_payment_requests_pending_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_pending_manager ON public.manual_payment_requests USING btree (manager_id, status, created_at DESC) WHERE ((status)::text = 'PENDING_MANAGER_REVIEW'::text);


--
-- Name: idx_manual_payment_requests_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_reservation ON public.manual_payment_requests USING btree (manager_id, reservation_id);


--
-- Name: idx_manual_payment_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_manual_payment_requests_status ON public.manual_payment_requests USING btree (status, created_at DESC);


--
-- Name: idx_payment_transactions_manager_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_transactions_manager_invoice ON public.payment_transactions USING btree (manager_id, invoice_id);


--
-- Name: idx_payment_transactions_manager_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_transactions_manager_reservation ON public.payment_transactions USING btree (manager_id, reservation_id);


--
-- Name: idx_payment_transactions_manager_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_transactions_manager_session ON public.payment_transactions USING btree (manager_id, session_id);


--
-- Name: idx_payment_transactions_reservation_success; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_transactions_reservation_success ON public.payment_transactions USING btree (manager_id, reservation_id, status);


--
-- Name: idx_reservation_request_idempotency_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservation_request_idempotency_customer ON public.reservation_request_idempotency USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_reservations_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reservations_time ON public.reservations USING btree (manager_id, station_id, start_time, end_time);


--
-- Name: idx_session_events_manager_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_events_manager_session ON public.session_events USING btree (manager_id, session_id, sequence_no);


--
-- Name: idx_session_orders_manager_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_orders_manager_session ON public.session_orders USING btree (manager_id, session_id, created_at DESC);


--
-- Name: idx_session_participants_manager_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_session_participants_manager_customer ON public.session_participants USING btree (manager_id, customer_id, created_at DESC);


--
-- Name: idx_trial_device_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trial_device_audit_created_at ON public.trial_device_audit_log USING btree (created_at DESC);


--
-- Name: idx_trial_devices_manager_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trial_devices_manager_id ON public.trial_devices USING btree (manager_id);


--
-- Name: ix_manager_entitlements_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_manager_entitlements_expiry ON public.manager_entitlements USING btree (status, expires_at);


--
-- Name: ix_manager_entitlements_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_manager_entitlements_manager ON public.manager_entitlements USING btree (manager_id);


--
-- Name: ix_manager_entitlements_manager_expiry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_manager_entitlements_manager_expiry ON public.manager_entitlements USING btree (manager_id, expires_at DESC);


--
-- Name: uq_customer_transactions_manager_local_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customer_transactions_manager_local_id ON public.customer_transactions USING btree (manager_id, local_id) WHERE (local_id IS NOT NULL);


--
-- Name: uq_customers_id_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_id_manager ON public.customers USING btree (id, manager_id);


--
-- Name: uq_customers_manager_invite_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_manager_invite_code ON public.customers USING btree (manager_id, invite_code) WHERE ((invite_code IS NOT NULL) AND ((invite_code)::text <> ''::text));


--
-- Name: uq_game_sessions_id_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_game_sessions_id_manager ON public.game_sessions USING btree (id, manager_id);


--
-- Name: uq_manual_payment_request_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_manual_payment_request_idempotency ON public.manual_payment_requests USING btree (manager_id, customer_id, idempotency_key);


--
-- Name: uq_reservation_allocations_id_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reservation_allocations_id_manager ON public.reservation_allocations USING btree (id, manager_id);


--
-- Name: uq_session_participants_session_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_session_participants_session_key ON public.session_participants USING btree (session_id, participant_key);


--
-- Name: uq_stations_id_manager; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_stations_id_manager ON public.stations USING btree (id, manager_id);


--
-- Name: uq_subscription_payment_requests_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_subscription_payment_requests_idempotency ON public.subscription_payment_requests USING btree (buyer_device_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: uq_trial_device_blocks_device_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_trial_device_blocks_device_id ON public.trial_device_blocks USING btree (device_id) WHERE (device_id IS NOT NULL);


--
-- Name: uq_trial_device_blocks_fingerprint; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_trial_device_blocks_fingerprint ON public.trial_device_blocks USING btree (device_fingerprint) WHERE (device_fingerprint IS NOT NULL);


--
-- Name: uq_trial_devices_fingerprint; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_trial_devices_fingerprint ON public.trial_devices USING btree (device_fingerprint) WHERE (device_fingerprint IS NOT NULL);


--
-- Name: ux_invoices_manager_reservation; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_invoices_manager_reservation ON public.invoices USING btree (manager_id, reservation_id) WHERE (reservation_id IS NOT NULL);


--
-- Name: ux_invoices_settlement_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_invoices_settlement_idempotency ON public.invoices USING btree (manager_id, settlement_idempotency_key) WHERE (settlement_idempotency_key IS NOT NULL);


--
-- Name: ux_manager_entitlements_one_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_manager_entitlements_one_active ON public.manager_entitlements USING btree (manager_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: game_sessions trg_release_session_customer_claims; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_release_session_customer_claims AFTER UPDATE OF status ON public.game_sessions FOR EACH ROW EXECUTE FUNCTION public.release_session_customer_claims();


--
-- Name: active_session_customer_claims active_session_customer_claims_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.active_session_customer_claims
    ADD CONSTRAINT active_session_customer_claims_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: active_session_customer_claims active_session_customer_claims_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.active_session_customer_claims
    ADD CONSTRAINT active_session_customer_claims_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: active_session_customer_claims active_session_customer_claims_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.active_session_customer_claims
    ADD CONSTRAINT active_session_customer_claims_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE CASCADE;


--
-- Name: api_idempotency_keys api_idempotency_keys_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.api_idempotency_keys
    ADD CONSTRAINT api_idempotency_keys_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: configuration_revisions configuration_revisions_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.configuration_revisions
    ADD CONSTRAINT configuration_revisions_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: customer_point_logs customer_point_logs_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_point_logs
    ADD CONSTRAINT customer_point_logs_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: customer_point_logs customer_point_logs_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_point_logs
    ADD CONSTRAINT customer_point_logs_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: customer_transactions customer_transactions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_transactions
    ADD CONSTRAINT customer_transactions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: customer_transactions customer_transactions_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_transactions
    ADD CONSTRAINT customer_transactions_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: customers customers_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: financial_audit_logs financial_audit_logs_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE SET NULL;


--
-- Name: financial_audit_logs financial_audit_logs_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE SET NULL;


--
-- Name: financial_audit_logs financial_audit_logs_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: financial_audit_logs financial_audit_logs_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payment_transactions(id) ON DELETE SET NULL;


--
-- Name: financial_audit_logs financial_audit_logs_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_audit_logs
    ADD CONSTRAINT financial_audit_logs_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE SET NULL;


--
-- Name: game_sessions fk_game_sessions_station_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_sessions
    ADD CONSTRAINT fk_game_sessions_station_manager FOREIGN KEY (station_id, manager_id) REFERENCES public.stations(id, manager_id) ON DELETE RESTRICT;


--
-- Name: gn_ledger fk_gn_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger
    ADD CONSTRAINT fk_gn_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE CASCADE;


--
-- Name: invoices fk_invoices_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk_invoices_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE RESTRICT;


--
-- Name: invoices fk_invoices_reservation_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk_invoices_reservation_manager FOREIGN KEY (reservation_id, manager_id) REFERENCES public.reservations(id, manager_id) ON DELETE RESTRICT;


--
-- Name: invoices fk_invoices_session_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk_invoices_session_manager FOREIGN KEY (session_id, manager_id) REFERENCES public.game_sessions(id, manager_id) ON DELETE RESTRICT;


--
-- Name: invoices fk_invoices_station_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk_invoices_station_manager FOREIGN KEY (station_id, manager_id) REFERENCES public.stations(id, manager_id) ON DELETE RESTRICT;


--
-- Name: lp_ledger fk_lp_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger
    ADD CONSTRAINT fk_lp_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE CASCADE;


--
-- Name: payment_transactions fk_payment_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT fk_payment_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE CASCADE;


--
-- Name: payment_transactions fk_payment_reservation_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT fk_payment_reservation_manager FOREIGN KEY (reservation_id, manager_id) REFERENCES public.reservations(id, manager_id) ON DELETE CASCADE;


--
-- Name: reservation_allocations fk_reservation_allocations_reservation_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT fk_reservation_allocations_reservation_manager FOREIGN KEY (reservation_id, manager_id) REFERENCES public.reservations(id, manager_id) ON DELETE CASCADE;


--
-- Name: reservation_allocations fk_reservation_allocations_station_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT fk_reservation_allocations_station_manager FOREIGN KEY (station_id, manager_id) REFERENCES public.stations(id, manager_id) ON DELETE CASCADE;


--
-- Name: reservations fk_reservations_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT fk_reservations_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE RESTRICT;


--
-- Name: reservations fk_reservations_station_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT fk_reservations_station_manager FOREIGN KEY (station_id, manager_id) REFERENCES public.stations(id, manager_id) ON DELETE RESTRICT;


--
-- Name: session_orders fk_session_orders_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT fk_session_orders_customer_manager FOREIGN KEY (target_customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE RESTRICT;


--
-- Name: session_orders fk_session_orders_session_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT fk_session_orders_session_manager FOREIGN KEY (session_id, manager_id) REFERENCES public.game_sessions(id, manager_id) ON DELETE CASCADE;


--
-- Name: session_participants fk_session_participants_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT fk_session_participants_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE RESTRICT;


--
-- Name: session_participants fk_session_participants_session_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT fk_session_participants_session_manager FOREIGN KEY (session_id, manager_id) REFERENCES public.game_sessions(id, manager_id) ON DELETE CASCADE;


--
-- Name: wallet_transactions fk_wallet_customer_manager; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT fk_wallet_customer_manager FOREIGN KEY (customer_id, manager_id) REFERENCES public.customers(id, manager_id) ON DELETE CASCADE;


--
-- Name: game_sessions game_sessions_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_sessions
    ADD CONSTRAINT game_sessions_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: game_sessions game_sessions_station_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.game_sessions
    ADD CONSTRAINT game_sessions_station_id_fkey FOREIGN KEY (station_id) REFERENCES public.stations(id) ON DELETE RESTRICT;


--
-- Name: gn_ledger gn_ledger_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger
    ADD CONSTRAINT gn_ledger_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: gn_ledger gn_ledger_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gn_ledger
    ADD CONSTRAINT gn_ledger_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: invoices invoices_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: invoices invoices_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE RESTRICT;


--
-- Name: invoices invoices_station_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_station_id_fkey FOREIGN KEY (station_id) REFERENCES public.stations(id) ON DELETE RESTRICT;


--
-- Name: lp_ledger lp_ledger_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger
    ADD CONSTRAINT lp_ledger_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: lp_ledger lp_ledger_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lp_ledger
    ADD CONSTRAINT lp_ledger_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: manager_device_bindings manager_device_bindings_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_device_bindings
    ADD CONSTRAINT manager_device_bindings_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: manager_entitlements manager_entitlements_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_entitlements
    ADD CONSTRAINT manager_entitlements_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: manager_payment_methods manager_payment_methods_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manager_payment_methods
    ADD CONSTRAINT manager_payment_methods_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: manual_payment_requests manual_payment_requests_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: manual_payment_requests manual_payment_requests_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE SET NULL;


--
-- Name: manual_payment_requests manual_payment_requests_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: manual_payment_requests manual_payment_requests_payment_method_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_payment_method_id_fkey FOREIGN KEY (payment_method_id) REFERENCES public.manager_payment_methods(id) ON DELETE SET NULL;


--
-- Name: manual_payment_requests manual_payment_requests_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE SET NULL;


--
-- Name: manual_payment_requests manual_payment_requests_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.manual_payment_requests
    ADD CONSTRAINT manual_payment_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES public.managers(id) ON DELETE SET NULL;


--
-- Name: notifications notifications_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: payment_transactions payment_transactions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: payment_transactions payment_transactions_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(id) ON DELETE RESTRICT;


--
-- Name: payment_transactions payment_transactions_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: payment_transactions payment_transactions_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;


--
-- Name: payment_transactions payment_transactions_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_transactions
    ADD CONSTRAINT payment_transactions_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE RESTRICT;


--
-- Name: reservation_allocations reservation_allocations_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT reservation_allocations_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: reservation_allocations reservation_allocations_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT reservation_allocations_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;


--
-- Name: reservation_allocations reservation_allocations_station_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_allocations
    ADD CONSTRAINT reservation_allocations_station_id_fkey FOREIGN KEY (station_id) REFERENCES public.stations(id) ON DELETE CASCADE;


--
-- Name: reservation_audit_logs reservation_audit_logs_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_audit_logs
    ADD CONSTRAINT reservation_audit_logs_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: reservation_audit_logs reservation_audit_logs_reservation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_audit_logs
    ADD CONSTRAINT reservation_audit_logs_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.reservations(id) ON DELETE CASCADE;


--
-- Name: reservation_request_idempotency reservation_request_idempotency_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_request_idempotency
    ADD CONSTRAINT reservation_request_idempotency_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: reservation_request_idempotency reservation_request_idempotency_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservation_request_idempotency
    ADD CONSTRAINT reservation_request_idempotency_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: reservations reservations_config_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_config_revision_id_fkey FOREIGN KEY (config_revision_id) REFERENCES public.configuration_revisions(id) ON DELETE RESTRICT;


--
-- Name: reservations reservations_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: reservations reservations_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: reservations reservations_station_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reservations
    ADD CONSTRAINT reservations_station_id_fkey FOREIGN KEY (station_id) REFERENCES public.stations(id) ON DELETE SET NULL;


--
-- Name: session_events session_events_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_events
    ADD CONSTRAINT session_events_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: session_events session_events_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_events
    ADD CONSTRAINT session_events_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE CASCADE;


--
-- Name: session_orders session_orders_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT session_orders_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: session_orders session_orders_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT session_orders_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE CASCADE;


--
-- Name: session_orders session_orders_target_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_orders
    ADD CONSTRAINT session_orders_target_customer_id_fkey FOREIGN KEY (target_customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: session_participants session_participants_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: session_participants session_participants_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: session_participants session_participants_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.game_sessions(id) ON DELETE CASCADE;


--
-- Name: stations stations_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stations
    ADD CONSTRAINT stations_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: subscription_payment_requests subscription_payment_requests_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_payment_requests
    ADD CONSTRAINT subscription_payment_requests_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: subscription_payment_requests subscription_payment_requests_reviewed_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_payment_requests
    ADD CONSTRAINT subscription_payment_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES public.managers(id);


--
-- Name: trial_devices trial_devices_manager_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trial_devices
    ADD CONSTRAINT trial_devices_manager_fk FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- Name: wallet_transactions wallet_transactions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: wallet_transactions wallet_transactions_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.managers(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict YZGvcabeo1NJx3Qiya5N2X4TEeVUrEqdvF7jEYOWnTOE7HyGdAyUp4cvkB3aSk4
