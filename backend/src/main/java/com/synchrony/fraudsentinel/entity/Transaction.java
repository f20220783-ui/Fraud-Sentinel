package com.synchrony.fraudsentinel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core transaction entity for the digital lending fraud pipeline.
 * The `behaviorEmbedding` column is a pgvector(1536) column; it is written
 * and read via native queries in {@link com.synchrony.fraudsentinel.repository.PgVectorRepository}
 * because standard JPA/Hibernate does not natively support the `vector` type.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    @Column(name = "transaction_id", updatable = false, nullable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "loan_application_id")
    private String loanApplicationId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "merchant_category")
    private String merchantCategory;

    @Column(name = "ml_fraud_score")
    private BigDecimal mlFraudScore;

    @Column(name = "final_decision")
    private String finalDecision;

    @Column(name = "decision_confidence")
    private BigDecimal decisionConfidence;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Transaction() {
    }

    // --- Getters and setters -------------------------------------------------

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(UUID deviceId) {
        this.deviceId = deviceId;
    }

    public String getLoanApplicationId() {
        return loanApplicationId;
    }

    public void setLoanApplicationId(String loanApplicationId) {
        this.loanApplicationId = loanApplicationId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public BigDecimal getMlFraudScore() {
        return mlFraudScore;
    }

    public void setMlFraudScore(BigDecimal mlFraudScore) {
        this.mlFraudScore = mlFraudScore;
    }

    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(String finalDecision) {
        this.finalDecision = finalDecision;
    }

    public BigDecimal getDecisionConfidence() {
        return decisionConfidence;
    }

    public void setDecisionConfidence(BigDecimal decisionConfidence) {
        this.decisionConfidence = decisionConfidence;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
