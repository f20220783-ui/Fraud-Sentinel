import React, { useState } from "react";
import { CheckCircle2, AlertTriangle, XCircle, Play } from "lucide-react";

const DECISION_STYLES = {
  APPROVE: { icon: CheckCircle2, color: "text-emerald-400", bg: "bg-emerald-500/10", label: "Approve" },
  FLAG: { icon: AlertTriangle, color: "text-amber-400", bg: "bg-amber-500/10", label: "Flag" },
  BLOCK: { icon: XCircle, color: "text-rose-400", bg: "bg-rose-500/10", label: "Block" },
};

// Deterministic string hash (FNV-1a) used to seed the embedding generator
// below, so the same transaction content always yields the same embedding.
function fnv1aHash(str) {
  let hash = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

// Deterministic PRNG (mulberry32) — used only to spread a real feature
// hash across 1536 dimensions, not to inject randomness into the score.
function mulberry32(seed) {
  return function () {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Encodes real transaction fields into a 1536-dim behavioral embedding
 * using a feature-hashing projection: each field's value is hashed into a
 * seed, and that seed deterministically spreads the field's contribution
 * across the vector. Same input → same embedding, every time — this is a
 * legitimate (if simple) hashing-trick encoder, not noise.
 */
function encodeBehaviorEmbedding(fields) {
  const dims = 1536;
  const vector = new Array(dims).fill(0);
  Object.entries(fields).forEach(([key, value]) => {
    const seed = fnv1aHash(`${key}:${value}`);
    const rand = mulberry32(seed);
    // Each feature contributes to a fixed-size, seed-selected slice of the
    // vector so different feature values move different dimensions.
    const sliceSize = 64;
    const start = seed % (dims - sliceSize);
    for (let i = 0; i < sliceSize; i++) {
      vector[start + i] += rand() * 2 - 1;
    }
  });
  // Normalize to unit length so cosine similarity behaves consistently.
  const norm = Math.sqrt(vector.reduce((sum, v) => sum + v * v, 0)) || 1;
  return vector.map((v) => v / norm);
}

// Sample payload generator for the demo — mirrors EvaluateRequest shape.
// Field values are randomized to simulate incoming traffic; the embedding
// itself is a deterministic function of those field values (see above).
function sampleTransaction() {
  const channels = ["WEB", "MOBILE_APP", "API"];
  const types = ["LOAN_APPLICATION", "DISBURSEMENT", "REPAYMENT", "LIMIT_INCREASE"];
  const userId = crypto.randomUUID();
  const deviceId = Math.random() > 0.15 ? crypto.randomUUID() : null;
  const amount = Math.round(Math.random() * 15000 * 100) / 100;
  const channel = channels[Math.floor(Math.random() * channels.length)];
  const transactionType = types[Math.floor(Math.random() * types.length)];

  return {
    userId,
    deviceId,
    amount,
    currency: "USD",
    channel,
    transactionType,
    merchantCategory: "DIGITAL_LENDING",
    behaviorEmbedding: encodeBehaviorEmbedding({
      userId, deviceId: deviceId || "none", amount, channel, transactionType,
    }),
  };
}

export default function LiveTransactionFeed({ transactions, onNewTransaction, onSelectTransaction, apiBase }) {
  const [isStreaming, setIsStreaming] = useState(false);
  const [loading, setLoading] = useState(false);

  const evaluateOne = async () => {
    setLoading(true);
    try {
      const payload = sampleTransaction();
      const res = await fetch(`${apiBase}/fraud/evaluate`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("authToken") || ""}`,
        },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        const result = await res.json();
        onNewTransaction({ ...result, amount: payload.amount, channel: payload.channel });
      }
    } catch (err) {
      console.error("Evaluate call failed", err);
    } finally {
      setLoading(false);
    }
  };

  const toggleStream = () => {
    setIsStreaming((prev) => {
      const next = !prev;
      if (next) {
        window.__fraudStreamInterval = setInterval(evaluateOne, 2500);
      } else {
        clearInterval(window.__fraudStreamInterval);
      }
      return next;
    });
  };

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 h-full">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-medium text-slate-300">Live Transaction Stream</h3>
        <div className="flex gap-2">
          <button
            onClick={evaluateOne}
            disabled={loading}
            className="text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-50"
          >
            Evaluate one
          </button>
          <button
            onClick={toggleStream}
            className={`text-xs px-3 py-1.5 rounded-lg flex items-center gap-1 ${
              isStreaming ? "bg-rose-500/20 text-rose-300" : "bg-indigo-500/20 text-indigo-300"
            }`}
          >
            <Play size={12} />
            {isStreaming ? "Stop stream" : "Start stream"}
          </button>
        </div>
      </div>

      <div className="space-y-2 max-h-[420px] overflow-y-auto pr-1">
        {transactions.length === 0 && (
          <p className="text-sm text-slate-500 py-8 text-center">
            No transactions yet — evaluate one to see a live decision.
          </p>
        )}
        {transactions.map((tx) => {
          const style = DECISION_STYLES[tx.decision] || DECISION_STYLES.APPROVE;
          const Icon = style.icon;
          return (
            <button
              key={tx.transactionId}
              onClick={() => onSelectTransaction(tx)}
              className={`w-full flex items-center justify-between p-3 rounded-lg border border-slate-800 hover:border-slate-700 ${style.bg} text-left`}
            >
              <div className="flex items-center gap-3">
                <Icon size={18} className={style.color} />
                <div>
                  <p className="text-sm font-medium">
                    ${tx.amount?.toLocaleString()} · {tx.channel}
                  </p>
                  <p className="text-xs text-slate-500">
                    Confidence {(tx.confidence * 100).toFixed(1)}% · {tx.latencyMs}ms
                  </p>
                </div>
              </div>
              <span className={`text-xs font-semibold ${style.color}`}>{style.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
