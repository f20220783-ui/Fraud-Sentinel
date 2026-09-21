package com.synchrony.fraudsentinel.service;

import com.synchrony.fraudsentinel.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FraudScoringServiceTest {

    private final FraudScoringService service = new FraudScoringService();

    private Transaction baseTransaction(BigDecimal amount, String channel,
                                          String type, boolean hasDevice) {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setUserId(UUID.randomUUID());
        tx.setDeviceId(hasDevice ? UUID.randomUUID() : null);
        tx.setAmount(amount);
        tx.setCurrency("USD");
        tx.setChannel(channel);
        tx.setTransactionType(type);
        return tx;
    }

    @Test
    void scoreIsDeterministic_sameInputProducesSameScore() {
        Transaction tx = baseTransaction(BigDecimal.valueOf(500), "MOBILE_APP",
                "REPAYMENT", true);
        List<String> rules = List.of();

        FraudScoringService.ScoredFeatures first = service.score(tx, rules);
        FraudScoringService.ScoredFeatures second = service.score(tx, rules);

        assertThat(first.score()).isEqualByComparingTo(second.score());
    }

    @Test
    void lowRiskTransaction_producesLowScore() {
        // Small repayment, mobile app, known device — the safest profile.
        Transaction tx = baseTransaction(BigDecimal.valueOf(100), "MOBILE_APP",
                "REPAYMENT", true);

        FraudScoringService.ScoredFeatures result = service.score(tx, List.of());

        assertThat(result.score()).isLessThan(BigDecimal.valueOf(0.3));
    }

    @Test
    void highRiskTransaction_producesHigherScoreThanLowRisk() {
        // Large loan application, API channel, missing device fingerprint,
        // multiple rule flags — the riskiest profile the engine models.
        Transaction lowRisk = baseTransaction(BigDecimal.valueOf(100), "MOBILE_APP",
                "REPAYMENT", true);
        Transaction highRisk = baseTransaction(BigDecimal.valueOf(45000), "API",
                "LOAN_APPLICATION", false);

        BigDecimal lowScore = service.score(lowRisk, List.of()).score();
        BigDecimal highScore = service.score(highRisk,
                List.of("HIGH_VALUE_TRANSACTION", "AUTOMATED_CHANNEL_LOAN_APP",
                        "MISSING_DEVICE_FINGERPRINT")).score();

        assertThat(highScore).isGreaterThan(lowScore);
    }

    @Test
    void missingDeviceFingerprint_increasesScoreVersusSameTransactionWithDevice() {
        Transaction withDevice = baseTransaction(BigDecimal.valueOf(2000), "WEB",
                "DISBURSEMENT", true);
        Transaction withoutDevice = baseTransaction(BigDecimal.valueOf(2000), "WEB",
                "DISBURSEMENT", false);

        BigDecimal scoreWithDevice = service.score(withDevice, List.of()).score();
        BigDecimal scoreWithoutDevice = service.score(withoutDevice, List.of()).score();

        assertThat(scoreWithoutDevice).isGreaterThan(scoreWithDevice);
    }

    @Test
    void scoreIsAlwaysWithinValidProbabilityRange() {
        Transaction extreme = baseTransaction(BigDecimal.valueOf(1_000_000), "API",
                "LOAN_APPLICATION", false);

        BigDecimal score = service.score(extreme,
                List.of("HIGH_VALUE_TRANSACTION", "AUTOMATED_CHANNEL_LOAN_APP",
                        "MISSING_DEVICE_FINGERPRINT")).score();

        assertThat(score).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(score).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    @Test
    void unknownChannelAndType_fallBackToMidRangeRiskWithoutError() {
        Transaction tx = baseTransaction(BigDecimal.valueOf(1000), "UNKNOWN_CHANNEL",
                "UNKNOWN_TYPE", true);

        FraudScoringService.ScoredFeatures result = service.score(tx, List.of());

        assertThat(result.channelRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.5000));
        assertThat(result.typeRisk()).isEqualByComparingTo(BigDecimal.valueOf(0.5000));
    }
}
