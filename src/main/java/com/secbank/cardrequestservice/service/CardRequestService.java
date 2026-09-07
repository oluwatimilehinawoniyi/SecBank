package com.secbank.cardrequestservice.service;

import com.secbank.cardrequestservice.domain.CardRequest;
import com.secbank.cardrequestservice.domain.CardRequestStatus;
import com.secbank.cardrequestservice.domain.Customer;
import com.secbank.cardrequestservice.domain.IdempotencyRecord;
import com.secbank.cardrequestservice.domain.RiskTier;
import com.secbank.cardrequestservice.dto.CardRequestCreateRequest;
import com.secbank.cardrequestservice.dto.CardRequestCreateResponse;
import com.secbank.cardrequestservice.dto.CardRequestStatusResponse;
import com.secbank.cardrequestservice.exception.CardRequestNotFoundException;
import com.secbank.cardrequestservice.exception.CustomerNotFoundException;
import com.secbank.cardrequestservice.exception.IdempotencyConflictException;
import com.secbank.cardrequestservice.exception.ReferenceGenerationException;
import com.secbank.cardrequestservice.repository.CardRequestRepository;
import com.secbank.cardrequestservice.repository.CustomerRepository;
import com.secbank.cardrequestservice.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class CardRequestService {

    private static final int MAX_REFERENCE_ATTEMPTS = 5;

    private final CardRequestRepository cardRequestRepository;
    private final CustomerRepository customerRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ReferenceGenerator referenceGenerator;

    public CardRequestService(CardRequestRepository cardRequestRepository,
                              CustomerRepository customerRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              ReferenceGenerator referenceGenerator) {
        this.cardRequestRepository = cardRequestRepository;
        this.customerRepository = customerRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.referenceGenerator = referenceGenerator;
    }

    @Transactional
    public CardRequestCreateResponse createCardRequest(
            CardRequestCreateRequest request, String idempotencyKey) {
        String requestHash = hashRequest(request);

        if (idempotencyKey != null) {
            Optional<CardRequestCreateResponse> replay =
                    replayIfKeySeenBefore(idempotencyKey, requestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        customerRepository.findByCustomerId(request.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer found with customerId " + request.customerId()));

        String reference = generateUniqueReference();
        CardRequest cardRequest =
                new CardRequest(reference, request.customerId(),
                        request.cardType());
        cardRequestRepository.save(cardRequest);

        CardRequestCreateResponse response = new CardRequestCreateResponse(
                cardRequest.getReference(),
                cardRequest.getStatus(),
                cardRequest.getCreatedAt());

        if (idempotencyKey != null) {
            idempotencyRecordRepository.save(new IdempotencyRecord(
                    idempotencyKey, requestHash, response.reference(),
                    response.status(), response.createdAt()));
        }

        return response;
    }

    @Transactional(readOnly = true)
    public CardRequestStatusResponse getStatus(String reference) {
        CardRequest cardRequest =
                cardRequestRepository.findByReference(reference)
                        .orElseThrow(
                                () -> new CardRequestNotFoundException(
                                        "No card request found with reference " + reference));

        return new CardRequestStatusResponse(
                cardRequest.getReference(),
                cardRequest.getCustomerId(),
                cardRequest.getCardType(),
                cardRequest.getStatus(),
                cardRequest.getRejectionReason(),
                cardRequest.getCreatedAt(),
                cardRequest.getUpdatedAt());
    }

    @Transactional
    public void advanceRequestStatuses() {
        for (CardRequest cardRequest : cardRequestRepository.findByStatus(
                CardRequestStatus.APPROVED)) {
            cardRequest.setStatus(CardRequestStatus.ISSUED);
        }
        for (CardRequest cardRequest : cardRequestRepository.findByStatus(
                CardRequestStatus.PENDING)) {
            advanceFromPending(cardRequest);
        }
    }

    private void advanceFromPending(CardRequest cardRequest) {
        Customer customer = customerRepository.findByCustomerId(
                        cardRequest.getCustomerId())
                .orElseThrow(() -> new CustomerNotFoundException(
                        "No customer found with customerId " + cardRequest.getCustomerId()));

        if (customer.getRiskTier() == RiskTier.HIGH_RISK) {
            cardRequest.setStatus(CardRequestStatus.REJECTED);
            cardRequest.setRejectionReason("Rejected due to customer risk profile");
        } else {
            cardRequest.setStatus(CardRequestStatus.APPROVED);
        }
    }

    private Optional<CardRequestCreateResponse> replayIfKeySeenBefore(
            String idempotencyKey, String requestHash) {
        return idempotencyRecordRepository.findById(idempotencyKey)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(
                                "Idempotency-Key " + idempotencyKey + " was already used with a different request body");
                    }
                    return new CardRequestCreateResponse(
                            record.getResponseReference(),
                            record.getResponseStatus(),
                            record.getResponseCreatedAt());
                });
    }

    private String generateUniqueReference() {
        for (int attempt =
             1; attempt <= MAX_REFERENCE_ATTEMPTS; attempt++) {
            String candidate = referenceGenerator.generate();
            if (!cardRequestRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new ReferenceGenerationException(
                "Failed to generate a unique card request reference after "
                        + MAX_REFERENCE_ATTEMPTS + " attempts");
    }

    private String hashRequest(CardRequestCreateRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical =
                    request.customerId() + "|" + request.cardType();
            byte[] hash = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available on this JVM", e);
        }
    }
}
