import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import XAiModal from "../XAiModal.jsx";

const sampleTransaction = {
  transactionId: "abcdef12-3456-7890-abcd-ef1234567890",
  decision: "FLAG",
  confidence: 0.62,
  mlFraudScore: 0.41,
  triggeredRules: ["HIGH_VALUE_TRANSACTION"],
  matchedPatterns: [
    { patternName: "device-farming-burst", category: "DEVICE_FARMING", similarity: 0.81 },
  ],
  featureImportance: { amount_normalized: 0.3, channel_risk: 0.2 },
  xaiNarrative: "Flagged due to a high-value transaction matching a known pattern.",
  latencyMs: 118,
};

describe("XAiModal", () => {
  it("renders nothing when no transaction is provided", () => {
    const { container } = render(
      <XAiModal transaction={null} onClose={() => {}} onFeedback={() => {}} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("displays the decision, confidence, and narrative", () => {
    render(<XAiModal transaction={sampleTransaction} onClose={() => {}} onFeedback={() => {}} />);

    expect(screen.getByText("FLAG")).toBeInTheDocument();
    expect(screen.getByText("62.0%")).toBeInTheDocument();
    expect(screen.getByText(/Flagged due to a high-value transaction/)).toBeInTheDocument();
  });

  it("lists triggered rules and matched patterns", () => {
    render(<XAiModal transaction={sampleTransaction} onClose={() => {}} onFeedback={() => {}} />);

    expect(screen.getByText("HIGH_VALUE_TRANSACTION")).toBeInTheDocument();
    expect(screen.getByText("device-farming-burst")).toBeInTheDocument();
  });

  it("calls onFeedback with CONFIRMED_FRAUD when that button is clicked", () => {
    const onFeedback = vi.fn();
    render(<XAiModal transaction={sampleTransaction} onClose={() => {}} onFeedback={onFeedback} />);

    fireEvent.click(screen.getByText(/Confirm Fraud/));

    expect(onFeedback).toHaveBeenCalledWith(sampleTransaction.transactionId, "CONFIRMED_FRAUD");
  });

  it("calls onFeedback with FALSE_POSITIVE when that button is clicked", () => {
    const onFeedback = vi.fn();
    render(<XAiModal transaction={sampleTransaction} onClose={() => {}} onFeedback={onFeedback} />);

    fireEvent.click(screen.getByText(/Mark False Positive/));

    expect(onFeedback).toHaveBeenCalledWith(sampleTransaction.transactionId, "FALSE_POSITIVE");
  });

  it("calls onClose when the close button is clicked", () => {
    const onClose = vi.fn();
    const { container } = render(
      <XAiModal transaction={sampleTransaction} onClose={onClose} onFeedback={() => {}} />
    );

    fireEvent.click(container.querySelector("button"));

    expect(onClose).toHaveBeenCalled();
  });
});
