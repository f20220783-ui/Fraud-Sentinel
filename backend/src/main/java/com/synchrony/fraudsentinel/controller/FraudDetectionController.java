package com.synchrony.fraudsentinel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchrony.fraudsentinel.dto.FraudAnalysisResult;
import com.synchrony.fraudsentinel.entity.Transaction;
import com.synchrony.fraudsentinel.repository.PgVectorRepository;
import com.synchrony.fraudsentinel.service.FraudDetectionEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:5173}")
public class FraudDetectionController {

    private final FraudDetectionEngine fraudDetectionEngine;
    private final PgVectorRepository pgVectorRepository;
    private final ObjectMapper objectMapper;

    public FraudDetectionController(FraudDetectionEngine fraudDetectionEngine,
                                     PgVectorRepository pgVectorRepository,
                                     ObjectMapper objectMapper) {
        this.fraudDetectionEngine = fraudDetectionEngine;
        this.pgVectorRepository = pgVectorRepository;
        this.objectMapper = objectMapper;
    }

    /** Request body for POST /api/v1/fraud/evaluate. */
    public record EvaluateRequest(
            @NotNull UUID userId,
            UUID deviceId,
            String loanApplicationId,
            @NotNull @DecimalMin("0.0") BigDecimal amount,
            @NotBlank String currency,
            @NotBlank String channel,
            @NotBlank String transactionType,
            String merchantCategory,
            @NotNull @Size(min = 1536, max = 1536) List<Double> behaviorEmbedding
    ) {
    }

    /** Analyst feedback request for POST /api/v1/fraud/feedback. */
    public record FeedbackRequest(
            @NotNull UUID transactionId,
            @NotBlank String feedback,      // CONFIRMED_FRAUD | FALSE_POSITIVE
            @NotBlank String analystId,
            String suggestedPatternName,
            String suggestedCategory
    ) {
    }

    @PostMapping("/evaluate")
    public ResponseEntity<FraudAnalysisResult> evaluate(@Valid @RequestBody EvaluateRequest req) throws Exception {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setUserId(req.userId());
        tx.setDeviceId(req.deviceId());
        tx.setLoanApplicationId(req.loanApplicationId());
        tx.setAmount(req.amount());
        tx.setCurrency(req.currency());
        tx.setChannel(req.channel());
        tx.setTransactionType(req.transactionType());
        tx.setMerchantCategory(req.merchantCategory());

        String embeddingLiteral = toVectorLiteral(req.behaviorEmbedding());

        FraudAnalysisResult result = fraudDetectionEngine.evaluate(tx, embeddingLiteral);

        // Persist the full transaction row exactly as scored — single
        // source-of-truth write, no separate placeholder insert.
        pgVectorRepository.insertTransaction(
                tx.getTransactionId(), tx.getUserId(), tx.getDeviceId(), tx.getLoanApplicationId(),
                tx.getAmount(), tx.getCurrency(), tx.getChannel(), tx.getTransactionType(),
                tx.getMerchantCategory(), embeddingLiteral, result.getMlFraudScore(),
                objectMapper.writeValueAsString(result.getTriggeredRules()),
                result.getDecision(), result.getConfidence(), result.getLatencyMs()
        );

        // Write the immutable audit trail for this decision.
        pgVectorRepository.insertAuditLog(
                tx.getTransactionId(), result.getDecision(), result.getConfidence(),
                objectMapper.writeValueAsString(result.getTriggeredRules()),
                objectMapper.writeValueAsString(result.getFeatureImportance()),
                result.getXaiNarrative(), result.getBedrockModelId(), result.getBedrockRequestId()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/insights")
    public ResponseEntity<Map<String, Object>> insights() {
        Map<String, Object> stats = pgVectorRepository.fetchDashboardMetrics();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@Valid @RequestBody FeedbackRequest req) {
        pgVectorRepository.recordAnalystFeedback(req.transactionId(), req.feedback(), req.analystId());

        if ("CONFIRMED_FRAUD".equals(req.feedback())) {
            pgVectorRepository.promoteTransactionToFraudPattern(
                    req.transactionId(),
                    req.suggestedPatternName() != null ? req.suggestedPatternName()
                            : "ANALYST_CONFIRMED_" + req.transactionId(),
                    req.suggestedCategory() != null ? req.suggestedCategory() : "FIRST_PARTY_FRAUD",
                    "MEDIUM"
            );
        }
        return ResponseEntity.noContent().build();
    }

    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) sb.append(',');
        }
        return sb.append(']').toString();
    }
}
