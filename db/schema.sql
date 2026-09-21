-- =========================================================================
-- FraudSentinel AI — Database Schema
-- PostgreSQL 15+ with pgvector extension
-- Synchrony Hackathon — Real-Time Fraud Detection Platform
-- =========================================================================

-- -------------------------------------------------------------------------
-- 0. Extensions
-- -------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -------------------------------------------------------------------------
-- 1. users
-- -------------------------------------------------------------------------
CREATE TABLE users (
    user_id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_customer_id VARCHAR(64) UNIQUE NOT NULL,
    full_name           VARCHAR(150) NOT NULL,
    email               VARCHAR(150) UNIQUE NOT NULL,
    phone               VARCHAR(20),
    kyc_verified        BOOLEAN NOT NULL DEFAULT FALSE,
    risk_tier           VARCHAR(16) NOT NULL DEFAULT 'LOW' CHECK (risk_tier IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    account_created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMPTZ,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_users_risk_tier ON users (risk_tier);

-- -------------------------------------------------------------------------
-- 2. devices
-- -------------------------------------------------------------------------
CREATE TABLE devices (
    device_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id             UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_fingerprint  VARCHAR(128) NOT NULL,
    device_type         VARCHAR(32),
    os_name             VARCHAR(64),
    browser             VARCHAR(64),
    ip_address          INET NOT NULL,
    geo_country         VARCHAR(64),
    geo_city            VARCHAR(64),
    is_trusted          BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_fingerprint)
);

CREATE INDEX idx_devices_user ON devices (user_id);
CREATE INDEX idx_devices_fingerprint ON devices (device_fingerprint);

-- -------------------------------------------------------------------------
-- 3. transactions
-- -------------------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id              UUID NOT NULL REFERENCES users(user_id),
    device_id            UUID REFERENCES devices(device_id),
    loan_application_id  VARCHAR(64),
    amount               NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    currency              CHAR(3) NOT NULL DEFAULT 'USD',
    channel              VARCHAR(32) NOT NULL CHECK (channel IN ('WEB','MOBILE_APP','API','IVR','BRANCH')),
    transaction_type     VARCHAR(32) NOT NULL CHECK (transaction_type IN ('LOAN_APPLICATION','DISBURSEMENT','REPAYMENT','LIMIT_INCREASE','ACCOUNT_UPDATE')),
    merchant_category     VARCHAR(64),
    behavior_embedding   vector(1536),
    ml_fraud_score        NUMERIC(5,4) CHECK (ml_fraud_score BETWEEN 0 AND 1),
    rule_flags            JSONB DEFAULT '[]'::jsonb,
    final_decision        VARCHAR(16) CHECK (final_decision IN ('APPROVE','FLAG','BLOCK')),
    decision_confidence   NUMERIC(5,4),
    latency_ms             INTEGER,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_user ON transactions (user_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
CREATE INDEX idx_transactions_decision ON transactions (final_decision);

-- HNSW index for real-time approximate nearest-neighbor cosine similarity
-- (requires pgvector >= 0.5.0). Falls back to IVFFlat below if unavailable.
CREATE INDEX idx_transactions_embedding_hnsw
    ON transactions
    USING hnsw (behavior_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- -------------------------------------------------------------------------
-- 4. fraud_patterns — historical attack signature library
-- -------------------------------------------------------------------------
CREATE TABLE fraud_patterns (
    pattern_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pattern_name         VARCHAR(128) NOT NULL,
    pattern_category     VARCHAR(48) NOT NULL CHECK (pattern_category IN
        ('SYNTHETIC_IDENTITY','ACCOUNT_TAKEOVER','LOAN_STACKING','BUST_OUT','VELOCITY_ABUSE','DEVICE_FARMING','FIRST_PARTY_FRAUD')),
    description          TEXT,
    embedding             vector(1536) NOT NULL,
    severity              VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    confirmed_case_count   INTEGER NOT NULL DEFAULT 1,
    source                VARCHAR(64) DEFAULT 'ANALYST_CONFIRMED',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_patterns_embedding_ivfflat
    ON fraud_patterns
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_fraud_patterns_category ON fraud_patterns (pattern_category);

-- -------------------------------------------------------------------------
-- 5. audit_logs — immutable XAI decision trail
-- -------------------------------------------------------------------------
CREATE TABLE audit_logs (
    audit_id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id         UUID NOT NULL REFERENCES transactions(transaction_id),
    decision                VARCHAR(16) NOT NULL,
    confidence               NUMERIC(5,4),
    risk_factors             JSONB NOT NULL DEFAULT '[]'::jsonb,
    feature_importance       JSONB NOT NULL DEFAULT '{}'::jsonb,
    xai_narrative             TEXT,
    bedrock_model_id          VARCHAR(128),
    bedrock_request_id        VARCHAR(128),
    analyst_feedback          VARCHAR(16) CHECK (analyst_feedback IN ('CONFIRMED_FRAUD','FALSE_POSITIVE', NULL)),
    analyst_id                 VARCHAR(64),
    feedback_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_transaction ON audit_logs (transaction_id);
CREATE INDEX idx_audit_logs_feedback ON audit_logs (analyst_feedback);

-- =========================================================================
-- Sample sub-50ms vector similarity queries
-- =========================================================================

-- (a) Top-5 nearest historical fraud patterns to a given transaction's
--     behavioral embedding, using cosine distance (HNSW-accelerated).
-- EXPLAIN ANALYZE
SELECT
    fp.pattern_id,
    fp.pattern_name,
    fp.pattern_category,
    fp.severity,
    1 - (fp.embedding <=> $1::vector) AS cosine_similarity
FROM fraud_patterns fp
ORDER BY fp.embedding <=> $1::vector
LIMIT 5;

-- (b) Real-time duplicate-behavior check: transactions in the last 24h
--     whose embedding is within a similarity threshold of the incoming one.
-- EXPLAIN ANALYZE
SELECT
    t.transaction_id,
    t.user_id,
    t.final_decision,
    1 - (t.behavior_embedding <=> $1::vector) AS similarity
FROM transactions t
WHERE t.created_at > now() - INTERVAL '24 hours'
  AND 1 - (t.behavior_embedding <=> $1::vector) > 0.85
ORDER BY t.behavior_embedding <=> $1::vector
LIMIT 10;

-- (c) Set search parameters for HNSW query-time recall/speed tradeoff
-- SET hnsw.ef_search = 40;
