package com.secbank.cardrequestservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "card_requests")
public class CardRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, updatable = false)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardRequestStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardRequest() {
    }

    public CardRequest(String reference, String customerId, CardType cardType) {
        this.reference = reference;
        this.customerId = customerId;
        this.cardType = cardType;
        this.status = CardRequestStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setStatus(CardRequestStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getCustomerId() {
        return customerId;
    }

    public CardType getCardType() {
        return cardType;
    }

    public CardRequestStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
