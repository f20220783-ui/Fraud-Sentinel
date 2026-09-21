# FraudSentinel AI — Presentation Slides
### Synchrony Hackathon Submission

---

## Slide 1 — Title
**FraudSentinel AI**
Real-Time Fraud Detection and Prevention in Digital Lending Ecosystems

Team: [Team Name]
Value proposition: Stop fraud in milliseconds, explain every decision in plain English.

---

## Slide 2 — The Problem
1. Digital lending fraud is evolving faster than static rule engines can adapt — synthetic identities, loan stacking, device farming, and bust-out schemes now blend into normal-looking behavior.
2. Most fraud models today are black boxes. A flagged applicant gets no explanation, compliance teams can't audit the decision, and analysts can't trust or improve the model.
3. Speed and trust are in tension: aggressive fraud filters slow down or wrongly block legitimate borrowers, damaging the customer relationship Synchrony depends on.

---

## Slide 3 — Proposed Solution
**FraudSentinel AI** is a hybrid, explainable fraud detection platform that combines:
1. Deterministic rule checks for known hard signals.
2. An ML fraud-probability score for statistical risk.
3. Vector similarity search (pgvector) against a continuously growing library of historical attack signatures.
4. AWS Bedrock-powered Explainable AI (XAI) that turns the combined evidence into a plain-English narrative for every decision — Approve, Flag, or Block.

---

## Slide 4 — High-Level Architecture
```
┌────────────┐    ┌──────────────────┐    ┌────────────────────┐
│ React UI   │───▶│ Spring Boot API   │───▶│ PostgreSQL+pgvector │
│ (Dashboard)│◀───│ Fraud Engine      │◀───│ transactions,       │
└────────────┘    │  - Rule checks    │    │ fraud_patterns,     │
                   │  - ML score (real) │    │ audit_logs          │
                   │  - Vector search  │    └────────────────────┘
                   │  - Bedrock XAI    │───▶ AWS Bedrock (Claude 3)
                   └──────────────────┘
```
Components:
1. React dashboard — live transaction feed, XAI modal, analyst feedback loop.
2. Spring Boot service layer — orchestrates rules, ML, vector search, and XAI in a single evaluate() call.
3. PostgreSQL with pgvector — HNSW-indexed behavioral embeddings for sub-50ms similarity search.
4. AWS Bedrock — guardrailed prompt templates generate the audit-ready explanation.

---

## Slide 5 — Deep-Dive: Hybrid Engine
1. Rule engine catches deterministic red flags (high-value transactions, missing device fingerprint, automated-channel loan applications) in under a millisecond.
2. The ML score layer is architected as a pluggable inference client — the deterministic logistic-regression scorer is a pluggable interface — swap in a hosted XGBoost/SageMaker endpoint without touching the API contract.
3. pgvector cosine similarity search matches the transaction's behavioral embedding against confirmed fraud patterns, surfacing the closest historical attacks and their similarity score.
4. Bedrock XAI receives only the computed evidence — never raw customer PII — and returns a structured JSON explanation, never the decision itself.

---

## Slide 6 — Responsible AI, Guardrails & Compliance
1. The LLM explains; it never decides. The approve/flag/block outcome is always computed deterministically before Bedrock is called.
2. Prompt templates restrict the model to supplied evidence only, block speculation about identity or protected characteristics, and enforce strict JSON output that is schema-validated before use.
3. Every decision writes an immutable audit_logs row — decision, confidence, risk factors, feature importance, and the exact XAI narrative shown to the analyst.
4. A Bedrock outage falls back to a deterministic, non-LLM explanation, so the platform never fails open or fails silently.

---

## Slide 7 — Scalability, Cloud Architecture & Security
1. Stateless Spring Boot service, horizontally scalable behind a load balancer; JWT bearer auth on every API route.
2. Zero hardcoded secrets — JWT signing key, DB credentials, and Bedrock model ID are all environment-variable driven.
3. HNSW vector indexing keeps similarity search sub-50ms even as the fraud_patterns library grows into the millions of rows.
4. Input validation (Bean Validation) and centralized exception handling ensure no stack traces or internal details leak to clients.

---

## Slide 8 — Key Features & Business Impact
1. Sub-200ms end-to-end decision latency, tracked live on the dashboard.
2. Every flagged transaction ships with an explanation an analyst can act on in seconds, not minutes.
3. The analyst feedback loop (Confirm Fraud / False Positive) immediately promotes confirmed cases into the pattern library, so the system gets sharper with every review.
4. Projected impact: reduced false-positive rate, faster analyst throughput, and an audit trail ready for regulatory review.

---

## Slide 9 — Future Roadmap
1. Replace the hand-calibrated logistic-regression weights with coefficients trained on Synchrony's historical lending data.
2. Add graph-based entity resolution to catch multi-account and synthetic-identity rings that single-transaction analysis misses.
3. Integrate directly into Synchrony's existing loan origination and servicing pipeline via the same REST contract.
4. Extend the Bedrock guardrails with a dedicated compliance-review prompt path for SAR (Suspicious Activity Report) drafting.

---

## Slide 10 — Demo Overview & Q&A
1. Live walkthrough: start the transaction stream, watch decisions render in real time.
2. Click a flagged transaction to open the XAI modal and read the Bedrock-generated explanation.
3. Submit analyst feedback and show the pattern library updating.
4. Questions and discussion.
