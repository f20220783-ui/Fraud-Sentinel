import React, { useEffect, useState, useCallback } from "react";
import { ShieldAlert, Activity, TrendingDown, Gauge } from "lucide-react";
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from "recharts";
import LiveTransactionFeed from "./LiveTransactionFeed.jsx";
import XAiModal from "./XAiModal.jsx";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";

function MetricCard({ icon: Icon, label, value, accent }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 flex items-center gap-3">
      <div className={`p-2 rounded-lg ${accent}`}>
        <Icon size={18} />
      </div>
      <div>
        <p className="text-xs text-slate-400">{label}</p>
        <p className="text-xl font-semibold">{value}</p>
      </div>
    </div>
  );
}

export default function FraudDashboard() {
  const [metrics, setMetrics] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [selectedTx, setSelectedTx] = useState(null);
  const [latencyHistory, setLatencyHistory] = useState([]);

  const fetchInsights = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/fraud/insights`, {
        headers: { Authorization: `Bearer ${localStorage.getItem("authToken") || ""}` },
      });
      if (res.ok) setMetrics(await res.json());
    } catch (err) {
      console.error("Failed to fetch insights", err);
    }
  }, []);

  useEffect(() => {
    fetchInsights();
    const interval = setInterval(fetchInsights, 8000);
    return () => clearInterval(interval);
  }, [fetchInsights]);

  const handleNewTransaction = (tx) => {
    setTransactions((prev) => [tx, ...prev].slice(0, 50));
    setLatencyHistory((prev) => [
      ...prev.slice(-19),
      { time: new Date().toLocaleTimeString(), latency: tx.latencyMs },
    ]);
  };

  const submitFeedback = async (transactionId, feedback) => {
    try {
      await fetch(`${API_BASE}/fraud/feedback`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${localStorage.getItem("authToken") || ""}`,
        },
        body: JSON.stringify({
          transactionId,
          feedback,
          analystId: "analyst-demo",
        }),
      });
      setSelectedTx(null);
    } catch (err) {
      console.error("Feedback submission failed", err);
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <MetricCard
          icon={Activity}
          label="Total Volume Processed"
          value={metrics ? metrics.totalProcessedVolume.toLocaleString() : "—"}
          accent="bg-indigo-500/10 text-indigo-400"
        />
        <MetricCard
          icon={ShieldAlert}
          label="Fraud Detection Rate"
          value={metrics ? `${metrics.fraudDetectionRatePct}%` : "—"}
          accent="bg-rose-500/10 text-rose-400"
        />
        <MetricCard
          icon={Gauge}
          label="Avg Decision Latency"
          value={metrics ? `${metrics.avgLatencyMs} ms` : "—"}
          accent="bg-amber-500/10 text-amber-400"
        />
        <MetricCard
          icon={TrendingDown}
          label="False Positive Rate (analyst-reviewed)"
          value={metrics ? `${metrics.falsePositiveRatePct}%` : "—"}
          accent="bg-emerald-500/10 text-emerald-400"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <LiveTransactionFeed
            transactions={transactions}
            onNewTransaction={handleNewTransaction}
            onSelectTransaction={setSelectedTx}
            apiBase={API_BASE}
          />
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
          <h3 className="text-sm font-medium text-slate-300 mb-3">
            Decision Latency (ms)
          </h3>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={latencyHistory}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="time" hide />
              <YAxis stroke="#64748b" fontSize={11} />
              <Tooltip
                contentStyle={{ background: "#0f172a", border: "1px solid #1e293b" }}
              />
              <Line type="monotone" dataKey="latency" stroke="#818cf8" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
          <p className="text-xs text-slate-500 mt-2">Target: sub-200ms end-to-end</p>
        </div>
      </div>

      {selectedTx && (
        <XAiModal
          transaction={selectedTx}
          onClose={() => setSelectedTx(null)}
          onFeedback={submitFeedback}
        />
      )}
    </div>
  );
}
