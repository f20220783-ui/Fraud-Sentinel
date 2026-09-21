package com.synchrony.fraudsentinel.service;

import com.synchrony.fraudsentinel.dto.FraudAnalysisResult;
import com.synchrony.fraudsentinel.entity.Transaction;
import com.synchrony.fraudsentinel.repository.PgVectorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the hybrid fraud-detection pipeline:
 *   1. Rule-based checks       (deterministic, sub-millisecond)
 *   2. ML fraud score          (real logistic-regression model — see FraudScoringService)
 *   3. pgvector pattern match  (cosine similarity vs. known attack signatures)
 *   4. Bedrock XAI explanation (plain-English narrative for analysts)
 *
 * Every stage is deterministic given the same input: no randomness, no
 * placeholder values. {@link FraudScoringService} owns the ML scoring
 * model and can be swapped for a hosted endpoint client without changing
 * this orchestration logic.
 */
@Service
public class FraudDetectionEngine {

    private static final BigDecimal BLOCK_THRESHOLD = BigDecimal.valueOf(0.85);
    private static final BigDecimal FLAG_THRESHOLD = BigDecimal.valueOf(0.45);
    private static final BigDecimal PATTERN_MATCH_THRESHOLD = BigDecimal.valueOf(0.75);

    @Value("${bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String bedrockModelId;

    private final PgVectorRepository pgVectorRepository;
    private final BedrockXAiService bedrockXAiService;
    private final FraudScoringService fraudScoringService;

    public FraudDetectionEngine(PgVectorRepository pgVectorRepository,
                                 BedrockXAiService bedrockXAiService,
                                 FraudScoringService fraudScoringService) {
        this.pgVectorRepository = pgVectorRepository;
        this.bedrockXAiService = bedrockXAiService;
        this.fraudScoringService = fraudScoringService;
    }

    public FraudAnalysisResult evaluate(Transaction transaction, String embeddingLiteral) {
        long start = System.currentTimeMillis();

        List<String> triggeredRules = runRuleChecks(transaction);
        FraudScoringService.ScoredFeatures scored = fraudScoringService.score(transaction, triggeredRules);
        BigDecimal mlScore = scored.score();

        List<PgVectorRepository.PatternMatch> matches =
                pgVectorRepository.findNearestFraudPatterns(embeddingLiteral, 5);
        List<FraudAnalysisResult.MatchedPattern> matchedPatterns = matches.stream()
                .filter(m -> m.cosineSimilarity().compareTo(PATTERN_MATCH_THRESHOLD) > 0)
                .map(m -> new FraudAnalysisResult.MatchedPattern(
                        m.patternName(), m.category(), m.cosineSimilarity()))
                .collect(Collectors.toList());

        BigDecimal combinedScore = combineScores(mlScore, triggeredRules, matchedPatterns);
        String decision = decide(combinedScore);

        BedrockXAiService.XaiExplanation xai = bedrockXAiService.explain(
                summarize(transaction),
                triggeredRules,
                mlScore,
                matchedPatterns.stream().map(FraudAnalysisResult.MatchedPattern::getPatternName)
                        .collect(Collectors.toList())
        );

        // Feature importance: prefer Bedrock's own attribution when it returns
        // one; otherwise fall back to the actual scoring-model features (never
        // a placeholder) so the modal always shows real, traceable numbers.
        Map<String, BigDecimal> featureImportance = new LinkedHashMap<>(xai.featureImportance());
        if (featureImportance.isEmpty()) {
            featureImportance.put("amount_normalized", scored.amountNorm());
            featureImportance.put("rule_flags_normalized", scored.ruleCount());
            featureImportance.put("channel_risk", scored.channelRisk());
            featureImportance.put("transaction_type_risk", scored.typeRisk());
            featureImportance.put("device_fingerprint_missing", scored.deviceMissing());
            featureImportance.put("pattern_similarity", matchedPatterns.isEmpty()
                    ? BigDecimal.ZERO
                    : matchedPatterns.get(0).getSimilarity());
        }

        int latency = (int) (System.currentTimeMillis() - start);

        return new FraudAnalysisResult(
                transaction.getTransactionId(),
                decision,
                combinedScore.setScale(4, RoundingMode.HALF_UP),
                mlScore,
                triggeredRules,
                matchedPatterns,
                featureImportance,
                xai.narrative(),
                bedrockModelId,
                xai.bedrockRequestId(),
                latency
        );
    }

    // -------------------------------------------------------------------
    // Rule engine
    // -------------------------------------------------------------------
    private List<String> runRuleChecks(Transaction tx) {
        List<String> flags = new ArrayList<>();

        if (tx.getAmount() != null && tx.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
            flags.add("HIGH_VALUE_TRANSACTION");
        }
        if ("LOAN_APPLICATION".equals(tx.getTransactionType())
                && "API".equals(tx.getChannel())) {
            flags.add("AUTOMATED_CHANNEL_LOAN_APP");
        }
        if (tx.getDeviceId() == null) {
            flags.add("MISSING_DEVICE_FINGERPRINT");
        }
        // Additional deterministic checks (velocity, geo-mismatch, blacklist,
        // stacking detection) plug in here without touching the ML/XAI layers.
        return flags;
    }

    private BigDecimal combineScores(BigDecimal mlScore, List<String> rules,
                                      List<FraudAnalysisResult.MatchedPattern> patterns) {
        BigDecimal patternBoost = patterns.isEmpty() ? BigDecimal.ZERO
                : patterns.get(0).getSimilarity().multiply(BigDecimal.valueOf(0.4));
        BigDecimal ruleBoost = BigDecimal.valueOf(Math.min(rules.size() * 0.08, 0.24));
        BigDecimal combined = mlScore.multiply(BigDecimal.valueOf(0.6))
                .add(patternBoost)
                .add(ruleBoost);
        return combined.min(BigDecimal.ONE);
    }

    private String decide(BigDecimal combinedScore) {
        if (combinedScore.compareTo(BLOCK_THRESHOLD) >= 0) return "BLOCK";
        if (combinedScore.compareTo(FLAG_THRESHOLD) >= 0) return "FLAG";
        return "APPROVE";
    }

    private String summarize(Transaction tx) {
        return "%s of %s %s via %s channel (type: %s)".formatted(
                tx.getTransactionType(), tx.getAmount(), tx.getCurrency(),
                tx.getChannel(), tx.getTransactionType());
    }
}
