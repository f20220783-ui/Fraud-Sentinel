package com.synchrony.fraudsentinel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO returned by POST /api/v1/fraud/evaluate.
 * Combines the rule-engine, ML score, pgvector pattern match, and Bedrock
 * XAI narrative into a single explainable decision object.
 */
public class FraudAnalysisResult {

    private UUID transactionId;
    private String decision;              // APPROVE | FLAG | BLOCK
    private BigDecimal confidence;        // 0.0 - 1.0
    private BigDecimal mlFraudScore;
    private List<String> triggeredRules;
    private List<MatchedPattern> matchedPatterns;
    private Map<String, BigDecimal> featureImportance;
    private String xaiNarrative;
    private String bedrockModelId;
    private String bedrockRequestId;
    private int latencyMs;

    public FraudAnalysisResult() {
    }

    public FraudAnalysisResult(UUID transactionId, String decision, BigDecimal confidence,
                                BigDecimal mlFraudScore, List<String> triggeredRules,
                                List<MatchedPattern> matchedPatterns,
                                Map<String, BigDecimal> featureImportance,
                                String xaiNarrative, String bedrockModelId,
                                String bedrockRequestId, int latencyMs) {
        this.transactionId = transactionId;
        this.decision = decision;
        this.confidence = confidence;
        this.mlFraudScore = mlFraudScore;
        this.triggeredRules = triggeredRules;
        this.matchedPatterns = matchedPatterns;
        this.featureImportance = featureImportance;
        this.xaiNarrative = xaiNarrative;
        this.bedrockModelId = bedrockModelId;
        this.bedrockRequestId = bedrockRequestId;
        this.latencyMs = latencyMs;
    }

    public static class MatchedPattern {
        private String patternName;
        private String category;
        private BigDecimal similarity;

        public MatchedPattern() {
        }

        public MatchedPattern(String patternName, String category, BigDecimal similarity) {
            this.patternName = patternName;
            this.category = category;
            this.similarity = similarity;
        }

        public String getPatternName() {
            return patternName;
        }

        public void setPatternName(String patternName) {
            this.patternName = patternName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public BigDecimal getSimilarity() {
            return similarity;
        }

        public void setSimilarity(BigDecimal similarity) {
            this.similarity = similarity;
        }
    }

    // --- Getters and setters -------------------------------------------------

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public BigDecimal getMlFraudScore() {
        return mlFraudScore;
    }

    public void setMlFraudScore(BigDecimal mlFraudScore) {
        this.mlFraudScore = mlFraudScore;
    }

    public List<String> getTriggeredRules() {
        return triggeredRules;
    }

    public void setTriggeredRules(List<String> triggeredRules) {
        this.triggeredRules = triggeredRules;
    }

    public List<MatchedPattern> getMatchedPatterns() {
        return matchedPatterns;
    }

    public void setMatchedPatterns(List<MatchedPattern> matchedPatterns) {
        this.matchedPatterns = matchedPatterns;
    }

    public Map<String, BigDecimal> getFeatureImportance() {
        return featureImportance;
    }

    public void setFeatureImportance(Map<String, BigDecimal> featureImportance) {
        this.featureImportance = featureImportance;
    }

    public String getXaiNarrative() {
        return xaiNarrative;
    }

    public void setXaiNarrative(String xaiNarrative) {
        this.xaiNarrative = xaiNarrative;
    }

    public String getBedrockModelId() {
        return bedrockModelId;
    }

    public void setBedrockModelId(String bedrockModelId) {
        this.bedrockModelId = bedrockModelId;
    }

    public String getBedrockRequestId() {
        return bedrockRequestId;
    }

    public void setBedrockRequestId(String bedrockRequestId) {
        this.bedrockRequestId = bedrockRequestId;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }
}
