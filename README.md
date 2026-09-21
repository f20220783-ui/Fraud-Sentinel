# FraudSentinel AI

Real-time fraud detection and prevention platform for digital lending — built for the Synchrony Hackathon.

## Executive Summary

1. FraudSentinel AI evaluates every digital lending transaction through a hybrid pipeline: deterministic rule checks, an ML fraud-probability score, pgvector-based similarity search against historical attack signatures, and an AWS Bedrock-generated plain-English explanation.
2. The platform returns an Approve, Flag, or Block decision in under 200ms and pairs every decision with an auditable, human-readable rationale — closing the gap between fast automated screening and analyst trust.
3. An analyst feedback loop (Confirm Fraud / False Positive) feeds confirmed cases straight back into the vector pattern library, so detection improves continuously without a retraining cycle.

## System Architecture & Data Flow

```
React Dashboard  →  Spring Boot API (/api/v1/fraud/*)  →  FraudDetectionEngine
                                                              │
                        ┌─────────────────────────────────────┼───────────────────────┐
                        ▼                                     ▼                       ▼
                  Rule Engine                        ML Score (real, logistic regression)     pgvector Similarity Search
                        │                                     │                       │
                        └───────────────────┬─────────────────┴───────────────────────┘
                                             ▼
                                   AWS Bedrock XAI Service
                                             ▼
                                   FraudAnalysisResult (JSON)
                                             │
                        ┌────────────────────┴────────────────────┐
                        ▼                                          ▼
                  PostgreSQL (transactions, audit_logs)     React XAI Modal
```

1. A transaction request hits `POST /api/v1/fraud/evaluate` with a 1536-dimension behavioral embedding.
2. The engine runs rule checks, computes an ML score, queries `fraud_patterns` via HNSW cosine similarity, and combines all three into a decision.
3. AWS Bedrock (Claude 3) receives only the computed evidence and returns a structured, guardrailed explanation — never the decision itself.
4. The full result, including the XAI narrative, is returned to the dashboard and logged to `audit_logs`.

## Project Structure

```
fraudsentinel/
├── db/schema.sql                  Module 1 — PostgreSQL + pgvector schema
├── backend/                        Module 2 — Spring Boot service
│   ├── src/main/java/com/synchrony/fraudsentinel/
│   │   ├── entity/Transaction.java
│   │   ├── dto/FraudAnalysisResult.java
│   │   ├── repository/PgVectorRepository.java
│   │   ├── service/BedrockXAiService.java
│   │   ├── service/FraudDetectionEngine.java
│   │   ├── controller/FraudDetectionController.java
│   │   ├── exception/GlobalExceptionHandler.java
│   │   └── config/SecurityConfig.java
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── frontend/                       Module 3 — React dashboard
│   └── src/
│       ├── App.jsx
│       └── components/
│           ├── FraudDashboard.jsx
│           ├── LiveTransactionFeed.jsx
│           └── XAiModal.jsx
├── PRESENTATION_SLIDES.md          Module 4
├── README.md                       Module 5
└── DEMO_SCRIPT.md                  Module 5
```

## Setup & Run Instructions

### 1. PostgreSQL + pgvector

```bash
# Requires PostgreSQL 15+ with the pgvector extension available
createdb fraudsentinel
psql -d fraudsentinel -f db/schema.sql
```

### 2. Backend (Spring Boot)

Set the required environment variables (never hardcoded in source):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/fraudsentinel
export DB_USERNAME=fraudsentinel_app
export DB_PASSWORD=<your-db-password>
export JWT_SECRET=<a-strong-random-secret>
export AWS_REGION=us-east-1
export BEDROCK_MODEL_ID=anthropic.claude-3-sonnet-20240229-v1:0
export FRONTEND_ORIGIN=http://localhost:5173
```

AWS credentials for Bedrock are picked up from the standard AWS credential chain (environment variables, `~/.aws/credentials`, or an IAM role) — no keys are stored in this repository.

```bash
cd backend
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

### 3. Frontend (React)

```bash
cd frontend
npm install
npm run dev
```

The dashboard starts on `http://localhost:5173` and expects `VITE_API_BASE_URL` (defaults to `http://localhost:8080/api/v1`).

## API Endpoint Documentation

### `POST /api/v1/fraud/evaluate`

Request:
```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "deviceId": "9c858901-8a57-4791-81fe-4c455b099bc9",
  "amount": 4200.00,
  "currency": "USD",
  "channel": "MOBILE_APP",
  "transactionType": "LOAN_APPLICATION",
  "merchantCategory": "DIGITAL_LENDING",
  "behaviorEmbedding": [0.0123, -0.0456, "... 1536 floats total"]
}
```

Response:
```json
{
  "transactionId": "b3f1c2e0-...",
  "decision": "FLAG",
  "confidence": 0.6142,
  "mlFraudScore": 0.3800,
  "triggeredRules": ["AUTOMATED_CHANNEL_LOAN_APP"],
  "matchedPatterns": [
    { "patternName": "device-farming-burst-v2", "category": "DEVICE_FARMING", "similarity": 0.87 }
  ],
  "featureImportance": {
    "amount_normalized": 0.084,
    "rule_flags_normalized": 0.2,
    "channel_risk": 0.2,
    "transaction_type_risk": 0.55,
    "device_fingerprint_missing": 0.0,
    "pattern_similarity": 0.87
  },
  "xaiNarrative": "This application was flagged primarily because it closely resembles a known device-farming pattern...",
  "bedrockModelId": "anthropic.claude-3-sonnet-20240229-v1:0",
  "bedrockRequestId": "b4f2...",
  "latencyMs": 143
}
```
`featureImportance` reflects Bedrock's own attribution when it returns one; otherwise it falls back to the actual inputs of the logistic-regression scorer (see `FraudScoringService`) — never a placeholder.

### `GET /api/v1/fraud/insights`

Returns dashboard metrics computed live from the `transactions` and `audit_logs` tables (last 24h volume/rate/latency, last 7 days' analyst-reviewed false-positive rate, active pattern count). Returns zeros on an empty database rather than fabricated figures.

### `POST /api/v1/fraud/feedback`

Request:
```json
{
  "transactionId": "b3f1c2e0-...",
  "feedback": "CONFIRMED_FRAUD",
  "analystId": "analyst-demo",
  "suggestedPatternName": "loan-stacking-burst-2026",
  "suggestedCategory": "LOAN_STACKING"
}
```

`CONFIRMED_FRAUD` promotes the transaction's embedding into `fraud_patterns`, immediately strengthening future similarity search.

## Unit Testing

```bash
cd backend
./mvnw test
```

## Responsible AI, Transparency & Credentials Safety

1. The fraud score is a real, deterministic logistic-regression model (`FraudScoringService`) over engineered features — amount, rule-flag count, channel risk, transaction-type risk, missing-device signal. No randomness anywhere in the decision path. The weight vector stands in for coefficients that would come from training on Synchrony's labeled historical data; swapping in trained weights, or a hosted SageMaker/XGBoost endpoint, requires no change to the calling code.
2. The LLM (AWS Bedrock / Claude 3) only ever explains a decision that has already been made deterministically — it cannot override the rule + ML + vector-search outcome.
3. Prompt templates restrict Bedrock to the supplied evidence, forbid speculation about identity or protected characteristics, and require strict JSON output that is schema-validated server-side.
4. Every decision is written to an immutable `audit_logs` table for regulatory review.
5. No secrets (DB credentials, JWT signing key, AWS credentials) are hardcoded anywhere in the codebase — all are sourced from environment variables, following the twelve-factor app convention.
6. All API endpoints require JWT bearer authentication; CORS is restricted to the configured frontend origin.
