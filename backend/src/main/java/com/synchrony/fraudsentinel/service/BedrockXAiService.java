package com.synchrony.fraudsentinel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Wraps AWS Bedrock (Claude 3) to turn a raw fraud signal bundle into a
 * structured, plain-English Explainable-AI (XAI) narrative for analysts
 * and, indirectly, for regulators/auditors.
 *
 * Guardrails enforced in the prompt template:
 *  - The model is restricted to the supplied evidence only (no outside
 *    knowledge, no speculation about the customer's identity or intent).
 *  - The model must return STRICT JSON matching a fixed schema — no prose
 *    outside the JSON object — which is validated before use.
 *  - The model is instructed to never fabricate a rule, score, or pattern
 *    name that was not present in the input evidence bundle.
 *  - Output is treated as advisory text only; it never overrides the
 *    deterministic decision computed by {@link FraudDetectionEngine}.
 */
@Service
public class BedrockXAiService {

    private static final Logger log = LoggerFactory.getLogger(BedrockXAiService.class);

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    @Value("${bedrock.model-id:anthropic.claude-3-sonnet-20240229-v1:0}")
    private String modelId;

    @Value("${bedrock.max-tokens:600}")
    private int maxTokens;

    public BedrockXAiService(BedrockRuntimeClient bedrockClient, ObjectMapper objectMapper) {
        this.bedrockClient = bedrockClient;
        this.objectMapper = objectMapper;
    }

    /** Structured XAI output returned to the fraud engine / API layer. */
    public record XaiExplanation(String narrative, List<String> riskFactors,
                                  Map<String, BigDecimal> featureImportance,
                                  BigDecimal llmConfidence, String bedrockRequestId) {
    }

    /**
     * Builds a locked-down prompt from evidence already computed
     * upstream (rule flags, ML score, matched historical patterns) and
     * asks Bedrock to explain — not to re-decide — the case.
     */
    public XaiExplanation explain(String transactionSummary,
                                    List<String> triggeredRules,
                                    BigDecimal mlFraudScore,
                                    List<String> matchedPatternNames) {
        String systemPrompt = buildGuardedSystemPrompt();
        String userPrompt = buildEvidencePrompt(transactionSummary, triggeredRules,
                mlFraudScore, matchedPatternNames);

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "max_tokens", maxTokens,
                    "temperature", 0.1,
                    "system", systemPrompt,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", userPrompt
                    ))
            ));

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            String rawText = root.path("content").get(0).path("text").asText();

            return parseAndValidate(rawText, response.responseMetadata().requestId());

        } catch (Exception ex) {
            log.error("Bedrock XAI invocation failed; falling back to deterministic summary", ex);
            return fallbackExplanation(triggeredRules, matchedPatternNames);
        }
    }

    private String buildGuardedSystemPrompt() {
        return """
                You are a fraud-risk explanation assistant for a regulated digital lending
                platform. You do NOT make the approve/flag/block decision — that decision is
                already final and made by a deterministic rule + ML + vector-similarity engine.
                Your only job is to explain, in plain English, WHY the evidence provided
                points to that risk level.

                STRICT RULES:
                1. Use ONLY the evidence given to you in the user message. Never invent a rule,
                   score, pattern name, or customer detail that was not supplied.
                2. Never speculate about the customer's identity, protected characteristics,
                   nationality, or personal life.
                3. Never suggest an alternative decision, discourage a hold, or advise
                   circumventing compliance review.
                4. Respond with STRICT JSON ONLY — no markdown, no prose outside the JSON,
                   matching exactly this schema:
                   {
                     "narrative": "2-4 sentence plain-English explanation",
                     "risk_factors": ["short factor 1", "short factor 2", ...],
                     "feature_importance": {"feature_name": 0.0-1.0, ...},
                     "llm_confidence": 0.0-1.0
                   }
                5. If the evidence is weak or contradictory, say so plainly in the narrative
                   rather than overstating certainty.
                """;
    }

    private String buildEvidencePrompt(String transactionSummary, List<String> triggeredRules,
                                        BigDecimal mlFraudScore, List<String> matchedPatternNames) {
        return """
                Transaction summary: %s
                Rule-engine flags triggered: %s
                ML fraud model score (0-1): %s
                Matched historical fraud pattern names (vector similarity): %s

                Produce the JSON explanation now.
                """.formatted(
                transactionSummary,
                triggeredRules.isEmpty() ? "none" : String.join(", ", triggeredRules),
                mlFraudScore != null ? mlFraudScore.toPlainString() : "unavailable",
                matchedPatternNames.isEmpty() ? "none" : String.join(", ", matchedPatternNames)
        );
    }

    @SuppressWarnings("unchecked")
    private XaiExplanation parseAndValidate(String rawJson, String requestId) throws Exception {
        // Guardrail: strip accidental markdown fences before parsing.
        String cleaned = rawJson.trim().replaceAll("^```json|```$", "").trim();
        JsonNode node = objectMapper.readTree(cleaned);

        String narrative = node.path("narrative").asText("Explanation unavailable.");
        List<String> riskFactors = objectMapper.convertValue(
                node.path("risk_factors"), List.class);
        Map<String, Object> rawImportance = objectMapper.convertValue(
                node.path("feature_importance"), Map.class);

        Map<String, BigDecimal> featureImportance = new java.util.LinkedHashMap<>();
        if (rawImportance != null) {
            rawImportance.forEach((k, v) -> featureImportance.put(k, new BigDecimal(v.toString())));
        }

        BigDecimal llmConfidence = node.has("llm_confidence")
                ? new BigDecimal(node.path("llm_confidence").asText("0.5"))
                : BigDecimal.valueOf(0.5);

        return new XaiExplanation(narrative,
                riskFactors != null ? riskFactors : List.of(),
                featureImportance, llmConfidence, requestId);
    }

    /** Deterministic, non-LLM fallback so the API never fails hard on a Bedrock outage. */
    private XaiExplanation fallbackExplanation(List<String> triggeredRules, List<String> patterns) {
        String narrative = triggeredRules.isEmpty() && patterns.isEmpty()
                ? "No rule or pattern evidence was available; decision based on ML score alone."
                : "Decision driven by rule flags [" + String.join(", ", triggeredRules) +
                  "] and pattern matches [" + String.join(", ", patterns) + "].";
        return new XaiExplanation(narrative, triggeredRules, Map.of(),
                BigDecimal.valueOf(0.3), "FALLBACK_NO_BEDROCK");
    }
}
