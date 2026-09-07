package com.secbank.cardrequestservice.dto;

import com.secbank.cardrequestservice.domain.CardRequestStatus;
import com.secbank.cardrequestservice.domain.CardType;

import java.time.Instant;

public record CardRequestStatusResponse(
        String reference,
        String customerId,
        CardType cardType,
        CardRequestStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
}
