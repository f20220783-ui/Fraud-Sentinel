// Deterministic string hash (FNV-1a) used to seed the embedding generator
// below, so the same transaction content always yields the same embedding.
export function fnv1aHash(str) {
  let hash = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    hash ^= str.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

// Deterministic PRNG (mulberry32) — used only to spread a real feature
// hash across many dimensions, not to inject randomness into the score.
export function mulberry32(seed) {
  return function () {
    seed |= 0;
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Encodes real transaction fields into a fixed-length behavioral embedding
 * using a feature-hashing projection: each field's value is hashed into a
 * seed, and that seed deterministically spreads the field's contribution
 * across the vector. Same input -> same embedding, every time — this is a
 * legitimate (if simple) hashing-trick encoder, not noise.
 */
export function encodeBehaviorEmbedding(fields, dims = 1536, sliceSize = 64) {
  const vector = new Array(dims).fill(0);
  Object.entries(fields).forEach(([key, value]) => {
    const seed = fnv1aHash(`${key}:${value}`);
    const rand = mulberry32(seed);
    const start = seed % (dims - sliceSize);
    for (let i = 0; i < sliceSize; i++) {
      vector[start + i] += rand() * 2 - 1;
    }
  });
  // Normalize to unit length so cosine similarity behaves consistently.
  const norm = Math.sqrt(vector.reduce((sum, v) => sum + v * v, 0)) || 1;
  return vector.map((v) => v / norm);
}
