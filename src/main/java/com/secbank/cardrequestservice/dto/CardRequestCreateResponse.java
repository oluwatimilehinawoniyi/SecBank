package com.secbank.cardrequestservice.dto;

import com.secbank.cardrequestservice.domain.CardRequestStatus;

import java.time.Instant;

public record CardRequestCreateResponse(
        String reference,
        CardRequestStatus status,
        Instant createdAt
) {
}
