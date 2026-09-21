package com.synchrony.fraudsentinel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchrony.fraudsentinel.dto.FraudAnalysisResult;
import com.synchrony.fraudsentinel.repository.PgVectorRepository;
import com.synchrony.fraudsentinel.service.FraudDetectionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the controller's own logic (request mapping, persistence calls,
 * vector-literal serialization) in isolation from Spring MVC / security,
 * which are exercised separately by the application's integration tests.
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectionControllerTest {

    @Mock
    private FraudDetectionEngine fraudDetectionEngine;

    @Mock
    private PgVectorRepository pgVectorRepository;

    private FraudDetectionController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new FraudDetectionController(fraudDetectionEngine, pgVectorRepository, objectMapper);
    }

    private FraudDetectionController.EvaluateRequest validRequest() {
        return new FraudDetectionController.EvaluateRequest(
                UUID.randomUUID(), UUID.randomUUID(), "LOAN-APP-001",
                BigDecimal.valueOf(2500), "USD", "WEB", "LOAN_APPLICATION",
                "DIGITAL_LENDING", java.util.Collections.nCopies(1536, 0.01)
        );
    }

    private FraudAnalysisResult stubResult(UUID transactionId) {
        return new FraudAnalysisResult(
                transactionId, "FLAG", BigDecimal.valueOf(0.6), BigDecimal.valueOf(0.4),
                List.of("HIGH_VALUE_TRANSACTION"), List.of(), Map.of(),
                "Test narrative", "test-model", "req-1", 120
        );
    }

    @Test
    void evaluate_persistsTransactionAndAuditLog_thenReturnsResult() throws Exception {
        FraudDetectionController.EvaluateRequest req = validRequest();
        UUID capturedId = UUID.randomUUID();
        FraudAnalysisResult stub = stubResult(capturedId);
        when(fraudDetectionEngine.evaluate(any(), anyString())).thenReturn(stub);

        ResponseEntity<FraudAnalysisResult> response = controller.evaluate(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(stub);
        verify(pgVectorRepository, times(1)).insertTransaction(
                any(), eq(req.userId()), eq(req.deviceId()), eq(req.loanApplicationId()),
                eq(req.amount()), eq(req.currency()), eq(req.channel()), eq(req.transactionType()),
                eq(req.merchantCategory()), anyString(), eq(stub.getMlFraudScore()),
                anyString(), eq(stub.getDecision()), eq(stub.getConfidence()), eq(stub.getLatencyMs())
        );
        verify(pgVectorRepository, times(1)).insertAuditLog(
                any(), eq(stub.getDecision()), eq(stub.getConfidence()), anyString(), anyString(),
                eq(stub.getXaiNarrative()), eq(stub.getBedrockModelId()), eq(stub.getBedrockRequestId())
        );
    }

    @Test
    void feedback_confirmedFraud_recordsFeedbackAndPromotesPattern() {
        UUID txId = UUID.randomUUID();
        FraudDetectionController.FeedbackRequest req = new FraudDetectionController.FeedbackRequest(
                txId, "CONFIRMED_FRAUD", "analyst-1", "custom-pattern", "LOAN_STACKING");

        ResponseEntity<Void> response = controller.feedback(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pgVectorRepository).recordAnalystFeedback(txId, "CONFIRMED_FRAUD", "analyst-1");
        verify(pgVectorRepository).promoteTransactionToFraudPattern(
                txId, "custom-pattern", "LOAN_STACKING", "MEDIUM");
    }

    @Test
    void feedback_falsePositive_recordsFeedbackButDoesNotPromotePattern() {
        UUID txId = UUID.randomUUID();
        FraudDetectionController.FeedbackRequest req = new FraudDetectionController.FeedbackRequest(
                txId, "FALSE_POSITIVE", "analyst-1", null, null);

        controller.feedback(req);

        verify(pgVectorRepository).recordAnalystFeedback(txId, "FALSE_POSITIVE", "analyst-1");
        verify(pgVectorRepository, never()).promoteTransactionToFraudPattern(any(), any(), any(), any());
    }

    @Test
    void insights_returnsWhateverTheRepositoryReports() {
        Map<String, Object> repoMetrics = Map.of("totalProcessedVolume", 42L);
        when(pgVectorRepository.fetchDashboardMetrics()).thenReturn(repoMetrics);

        ResponseEntity<Map<String, Object>> response = controller.insights();

        assertThat(response.getBody()).isEqualTo(repoMetrics);
    }
}
