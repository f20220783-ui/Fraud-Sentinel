package com.synchrony.fraudsentinel.service;

import com.synchrony.fraudsentinel.dto.FraudAnalysisResult;
import com.synchrony.fraudsentinel.entity.Transaction;
import com.synchrony.fraudsentinel.repository.PgVectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudDetectionEngineTest {

    @Mock
    private PgVectorRepository pgVectorRepository;

    @Mock
    private BedrockXAiService bedrockXAiService;

    private FraudDetectionEngine engine;
    private final FraudScoringService scoringService = new FraudScoringService();

    @BeforeEach
    void setUp() {
        engine = new FraudDetectionEngine(pgVectorRepository, bedrockXAiService, scoringService);
    }

    private Transaction lowRiskTransaction() {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setUserId(UUID.randomUUID());
        tx.setDeviceId(UUID.randomUUID());
        tx.setAmount(BigDecimal.valueOf(150));
        tx.setCurrency("USD");
        tx.setChannel("MOBILE_APP");
        tx.setTransactionType("REPAYMENT");
        return tx;
    }

    private Transaction highRiskTransaction() {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setUserId(UUID.randomUUID());
        tx.setDeviceId(null);
        tx.setAmount(BigDecimal.valueOf(48000));
        tx.setCurrency("USD");
        tx.setChannel("API");
        tx.setTransactionType("LOAN_APPLICATION");
        return tx;
    }

    private BedrockXAiService.XaiExplanation stubXaiExplanation() {
        return new BedrockXAiService.XaiExplanation(
                "Test narrative", List.of("TEST_FACTOR"), Map.of(),
                BigDecimal.valueOf(0.5), "req-123");
    }

    @Test
    void lowRiskTransactionWithNoPatternMatches_isApproved() {
        when(pgVectorRepository.findNearestFraudPatterns(anyString(), anyInt()))
                .thenReturn(List.of());
        when(bedrockXAiService.explain(anyString(), anyList(), any(), anyList()))
                .thenReturn(stubXaiExplanation());

        FraudAnalysisResult result = engine.evaluate(lowRiskTransaction(), "[0.1,0.2]");

        assertThat(result.getDecision()).isEqualTo("APPROVE");
    }

    @Test
    void highRiskTransactionWithStrongPatternMatch_isBlocked() {
        PgVectorRepository.PatternMatch strongMatch = new PgVectorRepository.PatternMatch(
                UUID.randomUUID(), "loan-stacking-burst", "LOAN_STACKING", "HIGH",
                BigDecimal.valueOf(0.95));
        when(pgVectorRepository.findNearestFraudPatterns(anyString(), anyInt()))
                .thenReturn(List.of(strongMatch));
        when(bedrockXAiService.explain(anyString(), anyList(), any(), anyList()))
                .thenReturn(stubXaiExplanation());

        FraudAnalysisResult result = engine.evaluate(highRiskTransaction(), "[0.9,0.9]");

        assertThat(result.getDecision()).isEqualTo("BLOCK");
        assertThat(result.getMatchedPatterns()).hasSize(1);
        assertThat(result.getMatchedPatterns().get(0).getPatternName())
                .isEqualTo("loan-stacking-burst");
    }

    @Test
    void weakPatternMatch_isFilteredOutOfMatchedPatterns() {
        PgVectorRepository.PatternMatch weakMatch = new PgVectorRepository.PatternMatch(
                UUID.randomUUID(), "unrelated-pattern", "DEVICE_FARMING", "LOW",
                BigDecimal.valueOf(0.40)); // below the 0.75 threshold
        when(pgVectorRepository.findNearestFraudPatterns(anyString(), anyInt()))
                .thenReturn(List.of(weakMatch));
        when(bedrockXAiService.explain(anyString(), anyList(), any(), anyList()))
                .thenReturn(stubXaiExplanation());

        FraudAnalysisResult result = engine.evaluate(lowRiskTransaction(), "[0.1,0.1]");

        assertThat(result.getMatchedPatterns()).isEmpty();
    }

    @Test
    void featureImportanceFallsBackToScoringModel_whenBedrockReturnsNone() {
        when(pgVectorRepository.findNearestFraudPatterns(anyString(), anyInt()))
                .thenReturn(List.of());
        when(bedrockXAiService.explain(anyString(), anyList(), any(), anyList()))
                .thenReturn(stubXaiExplanation()); // empty featureImportance map

        FraudAnalysisResult result = engine.evaluate(lowRiskTransaction(), "[0.1,0.1]");

        assertThat(result.getFeatureImportance()).containsKeys(
                "amount_normalized", "rule_flags_normalized", "channel_risk",
                "transaction_type_risk", "device_fingerprint_missing");
    }

    @Test
    void resultCarriesBedrockProvenanceFromExplanation() {
        when(pgVectorRepository.findNearestFraudPatterns(anyString(), anyInt()))
                .thenReturn(List.of());
        when(bedrockXAiService.explain(anyString(), anyList(), any(), anyList()))
                .thenReturn(stubXaiExplanation());

        FraudAnalysisResult result = engine.evaluate(lowRiskTransaction(), "[0.1,0.1]");

        assertThat(result.getBedrockRequestId()).isEqualTo("req-123");
    }

    // --- Mockito ArgumentMatchers shorthand (avoids extra static import clutter) ---
    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private static <T> List<T> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }

    private static BigDecimal any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
