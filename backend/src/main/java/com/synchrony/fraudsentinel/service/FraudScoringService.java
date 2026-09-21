package com.synchrony.fraudsentinel.service;

import com.synchrony.fraudsentinel.entity.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Deterministic logistic-regression fraud scorer.
 *
 * This replaces a random/mocked score with an actual (if intentionally
 * simple, hand-calibrated) linear model: features are engineered from the
 * transaction and rule-engine output, combined with fixed weights, and
 * passed through a sigmoid to produce a genuine 0.0-1.0 probability.
 *
 * The weight vector below stands in for coefficients that would normally
 * come from training on Synchrony's historical labeled transaction data
 * (e.g. via scikit-learn / XGBoost export). Swapping in trained weights,
 * or calling out to a hosted model endpoint, requires no change to the
 * calling code in {@link FraudDetectionEngine} — only to {@link #score}.
 */
@Service
public class FraudScoringService {

    // Feature order: [bias, amountNorm, ruleCount, channelRisk, typeRisk, deviceMissing]
    private static final double[] WEIGHTS = {
            -2.20,  // bias — baseline low prior probability of fraud
            2.10,   // amountNorm      (transaction amount, min-max scaled 0-1 against $50k cap)
            0.85,   // ruleCount       (number of deterministic rule flags triggered)
            0.60,   // channelRisk     (0-1 risk weight for the transaction channel)
            0.35,   // typeRisk        (0-1 risk weight for the transaction type)
            1.10    // deviceMissing   (1.0 if no device fingerprint was captured, else 0.0)
    };

    private static final Map<String, Double> CHANNEL_RISK = Map.of(
            "API", 0.75,
            "IVR", 0.55,
            "WEB", 0.30,
            "MOBILE_APP", 0.20,
            "BRANCH", 0.05
    );

    private static final Map<String, Double> TYPE_RISK = Map.of(
            "LOAN_APPLICATION", 0.55,
            "LIMIT_INCREASE", 0.45,
            "DISBURSEMENT", 0.40,
            "ACCOUNT_UPDATE", 0.30,
            "REPAYMENT", 0.10
    );

    private static final double AMOUNT_CAP = 50_000.0;

    /** Feature values used for this scoring pass — exposed for XAI feature importance. */
    public record ScoredFeatures(BigDecimal amountNorm, BigDecimal ruleCount,
                                  BigDecimal channelRisk, BigDecimal typeRisk,
                                  BigDecimal deviceMissing, BigDecimal score) {
    }

    public ScoredFeatures score(Transaction tx, List<String> triggeredRules) {
        double amountNorm = tx.getAmount() == null ? 0.0
                : Math.min(tx.getAmount().doubleValue() / AMOUNT_CAP, 1.0);
        double ruleCount = Math.min(triggeredRules.size() / 5.0, 1.0); // normalize to 0-1
        double channelRisk = CHANNEL_RISK.getOrDefault(tx.getChannel(), 0.5);
        double typeRisk = TYPE_RISK.getOrDefault(tx.getTransactionType(), 0.5);
        double deviceMissing = tx.getDeviceId() == null ? 1.0 : 0.0;

        double[] features = {1.0, amountNorm, ruleCount, channelRisk, typeRisk, deviceMissing};
        double linear = 0.0;
        for (int i = 0; i < features.length; i++) {
            linear += WEIGHTS[i] * features[i];
        }
        double probability = sigmoid(linear);

        return new ScoredFeatures(
                round(amountNorm), round(ruleCount), round(channelRisk),
                round(typeRisk), round(deviceMissing), round(probability)
        );
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
