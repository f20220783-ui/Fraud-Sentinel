import React from "react";
import FraudDashboard from "./components/FraudDashboard.jsx";

export default function App() {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-slate-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-indigo-500 flex items-center justify-center font-bold">
            FS
          </div>
          <h1 className="text-lg font-semibold">FraudSentinel AI</h1>
          <span className="text-xs text-slate-400 ml-2">
            Real-Time Fraud Detection · Synchrony Hackathon
          </span>
        </div>
        <span className="text-xs px-2 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
          Live
        </span>
      </header>
      <main className="p-6">
        <FraudDashboard />
      </main>
    </div>
  );
}
