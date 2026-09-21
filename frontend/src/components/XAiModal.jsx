import React from "react";
import { X, ThumbsUp, ThumbsDown } from "lucide-react";

export default function XAiModal({ transaction, onClose, onFeedback }) {
  if (!transaction) return null;

  const {
    transactionId, decision, confidence, mlFraudScore,
    triggeredRules = [], matchedPatterns = [], featureImportance = {},
    xaiNarrative, latencyMs,
  } = transaction;

  const decisionColor = {
    APPROVE: "text-emerald-400",
    FLAG: "text-amber-400",
    BLOCK: "text-rose-400",
  }[decision] || "text-slate-300";

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-lg w-full p-6 space-y-4">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-xs text-slate-500">Transaction {transactionId?.slice(0, 8)}</p>
            <h3 className={`text-xl font-semibold ${decisionColor}`}>{decision}</h3>
          </div>
          <button onClick={onClose} className="text-slate-500 hover:text-slate-300">
            <X size={20} />
          </button>
        </div>

        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="bg-slate-800/50 rounded-lg p-2">
            <p className="text-xs text-slate-500">Confidence</p>
            <p className="font-semibold">{(confidence * 100).toFixed(1)}%</p>
          </div>
          <div className="bg-slate-800/50 rounded-lg p-2">
            <p className="text-xs text-slate-500">ML Score</p>
            <p className="font-semibold">{(mlFraudScore * 100).toFixed(1)}%</p>
          </div>
          <div className="bg-slate-800/50 rounded-lg p-2">
            <p className="text-xs text-slate-500">Latency</p>
            <p className="font-semibold">{latencyMs}ms</p>
          </div>
        </div>

        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500 mb-1">
            AWS Bedrock XAI Explanation
          </p>
          <p className="text-sm text-slate-300 leading-relaxed">{xaiNarrative}</p>
        </div>

        {triggeredRules.length > 0 && (
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500 mb-1">Rule Flags</p>
            <div className="flex flex-wrap gap-1.5">
              {triggeredRules.map((rule) => (
                <span key={rule} className="text-xs px-2 py-1 rounded-full bg-slate-800 text-slate-300">
                  {rule}
                </span>
              ))}
            </div>
          </div>
        )}

        {matchedPatterns.length > 0 && (
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500 mb-1">
              Matched Historical Patterns
            </p>
            <div className="space-y-1">
              {matchedPatterns.map((p) => (
                <div key={p.patternName} className="flex justify-between text-sm">
                  <span className="text-slate-300">{p.patternName}</span>
                  <span className="text-slate-500">{(p.similarity * 100).toFixed(0)}% similar</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {Object.keys(featureImportance).length > 0 && (
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500 mb-1">Feature Importance</p>
            <div className="space-y-1.5">
              {Object.entries(featureImportance).map(([feature, weight]) => (
                <div key={feature}>
                  <div className="flex justify-between text-xs text-slate-400 mb-0.5">
                    <span>{feature}</span>
                    <span>{(weight * 100).toFixed(0)}%</span>
                  </div>
                  <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-indigo-500 rounded-full"
                      style={{ width: `${Math.min(weight * 100, 100)}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="flex gap-3 pt-2 border-t border-slate-800">
          <button
            onClick={() => onFeedback(transactionId, "CONFIRMED_FRAUD")}
            className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg bg-rose-500/10 text-rose-300 hover:bg-rose-500/20 text-sm"
          >
            <ThumbsDown size={14} /> Confirm Fraud
          </button>
          <button
            onClick={() => onFeedback(transactionId, "FALSE_POSITIVE")}
            className="flex-1 flex items-center justify-center gap-2 py-2 rounded-lg bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20 text-sm"
          >
            <ThumbsUp size={14} /> Mark False Positive
          </button>
        </div>
      </div>
    </div>
  );
}
