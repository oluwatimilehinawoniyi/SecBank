package com.secbank.cardrequestservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_reference", nullable = false)
    private String responseReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    private CardRequestStatus responseStatus;

    @Column(name = "response_created_at", nullable = false)
    private Instant responseCreatedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash,
                             String responseReference,
                             CardRequestStatus responseStatus,
                             Instant responseCreatedAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseReference = responseReference;
        this.responseStatus = responseStatus;
        this.responseCreatedAt = responseCreatedAt;
        this.recordedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponseReference() {
        return responseReference;
    }

    public CardRequestStatus getResponseStatus() {
        return responseStatus;
    }

    public Instant getResponseCreatedAt() {
        return responseCreatedAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
