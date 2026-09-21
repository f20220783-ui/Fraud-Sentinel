import { describe, it, expect } from "vitest";
import { encodeBehaviorEmbedding, fnv1aHash } from "../embedding.js";

describe("fnv1aHash", () => {
  it("is deterministic for the same input", () => {
    expect(fnv1aHash("amount:2500")).toBe(fnv1aHash("amount:2500"));
  });

  it("produces different hashes for different input", () => {
    expect(fnv1aHash("amount:2500")).not.toBe(fnv1aHash("amount:2501"));
  });
});

describe("encodeBehaviorEmbedding", () => {
  const sampleFields = {
    userId: "user-1",
    deviceId: "device-1",
    amount: 2500,
    channel: "WEB",
    transactionType: "LOAN_APPLICATION",
  };

  it("returns a vector of the requested dimensionality", () => {
    const vector = encodeBehaviorEmbedding(sampleFields, 1536);
    expect(vector).toHaveLength(1536);
  });

  it("is deterministic: identical transaction fields yield identical embeddings", () => {
    const first = encodeBehaviorEmbedding(sampleFields);
    const second = encodeBehaviorEmbedding(sampleFields);
    expect(first).toEqual(second);
  });

  it("produces a different embedding when a field value changes", () => {
    const first = encodeBehaviorEmbedding(sampleFields);
    const second = encodeBehaviorEmbedding({ ...sampleFields, amount: 9999 });
    expect(first).not.toEqual(second);
  });

  it("is approximately unit-normalized for cosine similarity", () => {
    const vector = encodeBehaviorEmbedding(sampleFields);
    const norm = Math.sqrt(vector.reduce((sum, v) => sum + v * v, 0));
    expect(norm).toBeCloseTo(1, 5);
  });

  it("contains no NaN or undefined values", () => {
    const vector = encodeBehaviorEmbedding(sampleFields);
    expect(vector.every((v) => Number.isFinite(v))).toBe(true);
  });

  it("handles an empty field set without throwing", () => {
    const vector = encodeBehaviorEmbedding({}, 128, 16);
    expect(vector).toHaveLength(128);
    expect(vector.every((v) => v === 0)).toBe(true);
  });
});
