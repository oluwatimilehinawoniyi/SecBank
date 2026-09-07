package com.secbank.cardrequestservice.dto;

import com.secbank.cardrequestservice.domain.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardRequestCreateRequest(
        @NotBlank String customerId,
        @NotNull CardType cardType
) {
}
