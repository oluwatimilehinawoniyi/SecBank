package com.secbank.cardrequestservice.controller;

import com.secbank.cardrequestservice.dto.CardRequestCreateRequest;
import com.secbank.cardrequestservice.dto.CardRequestCreateResponse;
import com.secbank.cardrequestservice.dto.CardRequestStatusResponse;
import com.secbank.cardrequestservice.dto.ErrorResponse;
import com.secbank.cardrequestservice.service.CardRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/card-requests")
@Tag(name = "Card Requests",
        description = "Create and check the status of card requests " +
                "against SecBank Core Banking")
public class CardRequestController {

    private final CardRequestService cardRequestService;

    public CardRequestController(CardRequestService cardRequestService) {
        this.cardRequestService = cardRequestService;
    }

    @PostMapping
    @Operation(summary = "Create a card request",
            description = "Creates a card request for an existing, seeded customer. Supports an optional "
                    + "Idempotency-Key header: the same key with the same body replays the original response, "
                    + "the same key with a different body is rejected as a conflict.")
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Card request created",
                    content = @Content(schema = @Schema(
                            implementation = CardRequestCreateResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Request body failed validation",
                    content = @Content(schema = @Schema(
                            implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Idempotency-Key was already used with a different request body",
                    content = @Content(schema = @Schema(
                            implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "No customer exists with the given customerId",
                    content = @Content(schema = @Schema(
                            implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CardRequestCreateResponse> create(
            @Valid @RequestBody CardRequestCreateRequest request,
            @Parameter(
                    description = "Optional client-supplied key for safe retries")
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {

        CardRequestCreateResponse response =
                cardRequestService.createCardRequest(request,
                        idempotencyKey);

        URI location = URI.create(
                "/api/v1/card-requests/" + response.reference());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Check the status of a card request")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Current status returned",
                    content = @Content(schema = @Schema(
                            implementation = CardRequestStatusResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "No card request exists with the given reference",
                    content = @Content(schema = @Schema(
                            implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CardRequestStatusResponse> getStatus(
            @Parameter(
                    description = "The opaque reference returned when the card request was created")
            @PathVariable String reference) {
        CardRequestStatusResponse response =
                cardRequestService.getStatus(reference);
        return ResponseEntity.ok(response);
    }
}
