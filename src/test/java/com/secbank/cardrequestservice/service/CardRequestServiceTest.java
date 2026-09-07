package com.secbank.cardrequestservice.service;

import com.secbank.cardrequestservice.domain.CardRequest;
import com.secbank.cardrequestservice.domain.CardRequestStatus;
import com.secbank.cardrequestservice.domain.CardType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CardRequestService, with all four repositories mocked.
 * The service is constructed manually in setUp() with mocks passed to its
 * real constructor, rather than relying on @InjectMocks, so exactly what
 * is wired is visible in the test itself.
 */
@ExtendWith(MockitoExtension.class)
class CardRequestServiceTest {

    @Mock
    private CardRequestRepository cardRequestRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private ReferenceGenerator referenceGenerator;

    private CardRequestService cardRequestService;

    private Customer standardCustomer;
    private Customer highRiskCustomer;

    @BeforeEach
    void setUp() {
        cardRequestService = new CardRequestService(
                cardRequestRepository, customerRepository,
                idempotencyRecordRepository, referenceGenerator);
        standardCustomer =
                new Customer("CUST-1001", "Ada Okafor", RiskTier.STANDARD);
        highRiskCustomer = new Customer("CUST-1002", "Bello Musa",
                RiskTier.HIGH_RISK);
    }

    // ---------------------------------------------------------------
    // createCardRequest
    // ---------------------------------------------------------------

    @Test
    void createCardRequest_throwsWhenCustomerDoesNotExist() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-9999", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-9999")).thenReturn(
                Optional.empty());

        assertThatThrownBy(
                () -> cardRequestService.createCardRequest(request, null))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(cardRequestRepository, never()).save(any());
    }

    @Test
    void createCardRequest_savesNewPendingRequestForExistingCustomer() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-TESTREF001");
        when(cardRequestRepository.existsByReference(
                "CR-TESTREF001")).thenReturn(false);

        CardRequestCreateResponse response =
                cardRequestService.createCardRequest(request, null);

        assertThat(response.reference()).isEqualTo("CR-TESTREF001");
        assertThat(response.status()).isEqualTo(CardRequestStatus.PENDING);
        verify(cardRequestRepository).save(any(CardRequest.class));
        verify(idempotencyRecordRepository, never()).save(any());
    }

    @Test
    void createCardRequest_retriesReferenceGenerationOnCollision() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-DUPLICATE",
                "CR-UNIQUE");
        when(cardRequestRepository.existsByReference(
                "CR-DUPLICATE")).thenReturn(true);
        when(cardRequestRepository.existsByReference(
                "CR-UNIQUE")).thenReturn(false);

        CardRequestCreateResponse response =
                cardRequestService.createCardRequest(request, null);

        assertThat(response.reference()).isEqualTo("CR-UNIQUE");
        verify(referenceGenerator, times(2)).generate();
    }

    @Test
    void createCardRequest_throwsWhenReferenceGenerationExhaustsAllAttempts() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-ALWAYS-TAKEN");
        when(cardRequestRepository.existsByReference(
                "CR-ALWAYS-TAKEN")).thenReturn(true);

        assertThatThrownBy(
                () -> cardRequestService.createCardRequest(request, null))
                .isInstanceOf(ReferenceGenerationException.class);

        verify(cardRequestRepository, never()).save(any());
    }

    @Test
    void createCardRequest_withUnseenIdempotencyKey_persistsIdempotencyRecord() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-TESTREF002");
        when(cardRequestRepository.existsByReference(
                "CR-TESTREF002")).thenReturn(false);
        when(idempotencyRecordRepository.findById("key-1")).thenReturn(
                Optional.empty());

        cardRequestService.createCardRequest(request, "key-1");

        verify(idempotencyRecordRepository).save(
                any(IdempotencyRecord.class));
    }

    @Test
    void createCardRequest_sameIdempotencyKeyAndSameBody_replaysWithoutReprocessing() {
        CardRequestCreateRequest request =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-TESTREF003");
        when(cardRequestRepository.existsByReference(
                "CR-TESTREF003")).thenReturn(false);
        when(idempotencyRecordRepository.findById("key-1")).thenReturn(
                Optional.empty());

        CardRequestCreateResponse firstResponse =
                cardRequestService.createCardRequest(request, "key-1");

        // Capture the record actually saved (with the real computed hash) and feed it back
        // as what the repository would return on a second lookup with the same key.
        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(captor.capture());
        when(idempotencyRecordRepository.findById("key-1")).thenReturn(
                Optional.of(captor.getValue()));

        CardRequestCreateResponse secondResponse =
                cardRequestService.createCardRequest(request, "key-1");

        assertThat(secondResponse).isEqualTo(firstResponse);
        // Only the first call should have touched the customer, reference generator, and card request repository.
        verify(customerRepository, times(1)).findByCustomerId("CUST-1001");
        verify(referenceGenerator, times(1)).generate();
        verify(cardRequestRepository, times(1)).save(
                any(CardRequest.class));
    }

    @Test
    void createCardRequest_sameIdempotencyKeyDifferentBody_throwsConflict() {
        CardRequestCreateRequest firstRequest =
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT);
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));
        when(referenceGenerator.generate()).thenReturn("CR-TESTREF004");
        when(cardRequestRepository.existsByReference(
                "CR-TESTREF004")).thenReturn(false);
        when(idempotencyRecordRepository.findById("key-2")).thenReturn(
                Optional.empty());

        cardRequestService.createCardRequest(firstRequest, "key-2");

        ArgumentCaptor<IdempotencyRecord> captor =
                ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(captor.capture());
        when(idempotencyRecordRepository.findById("key-2")).thenReturn(
                Optional.of(captor.getValue()));

        CardRequestCreateRequest differentRequest =
                new CardRequestCreateRequest("CUST-1001", CardType.CREDIT);

        assertThatThrownBy(() -> cardRequestService.createCardRequest(
                differentRequest, "key-2"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ---------------------------------------------------------------
    // getStatus
    // ---------------------------------------------------------------

    @Test
    void getStatus_returnsMappedResponseForExistingReference() {
        CardRequest cardRequest =
                new CardRequest("CR-EXIST01", "CUST-1001", CardType.DEBIT);
        when(cardRequestRepository.findByReference(
                "CR-EXIST01")).thenReturn(Optional.of(cardRequest));

        CardRequestStatusResponse response =
                cardRequestService.getStatus("CR-EXIST01");

        assertThat(response.reference()).isEqualTo("CR-EXIST01");
        assertThat(response.customerId()).isEqualTo("CUST-1001");
        assertThat(response.cardType()).isEqualTo(CardType.DEBIT);
        assertThat(response.status()).isEqualTo(CardRequestStatus.PENDING);
        assertThat(response.rejectionReason()).isNull();
    }

    @Test
    void getStatus_throwsWhenReferenceDoesNotExist() {
        when(cardRequestRepository.findByReference(
                "CR-MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> cardRequestService.getStatus("CR-MISSING"))
                .isInstanceOf(CardRequestNotFoundException.class);
    }

    // ---------------------------------------------------------------
    // advanceRequestStatuses
    // ---------------------------------------------------------------

    @Test
    void advanceRequestStatuses_movesPendingStandardCustomerRequestToApproved() {
        CardRequest pending =
                new CardRequest("CR-P1", "CUST-1001", CardType.DEBIT);
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.APPROVED)).thenReturn(List.of());
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.PENDING)).thenReturn(List.of(pending));
        when(customerRepository.findByCustomerId("CUST-1001")).thenReturn(
                Optional.of(standardCustomer));

        cardRequestService.advanceRequestStatuses();

        assertThat(pending.getStatus()).isEqualTo(
                CardRequestStatus.APPROVED);
    }

    @Test
    void advanceRequestStatuses_movesPendingHighRiskCustomerRequestToRejectedWithReason() {
        CardRequest pending =
                new CardRequest("CR-P2", "CUST-1002", CardType.CREDIT);
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.APPROVED)).thenReturn(List.of());
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.PENDING)).thenReturn(List.of(pending));
        when(customerRepository.findByCustomerId("CUST-1002")).thenReturn(
                Optional.of(highRiskCustomer));

        cardRequestService.advanceRequestStatuses();

        assertThat(pending.getStatus()).isEqualTo(
                CardRequestStatus.REJECTED);
        assertThat(pending.getRejectionReason()).isNotBlank();
    }

    @Test
    void advanceRequestStatuses_movesApprovedRequestToIssued() {
        CardRequest approved =
                new CardRequest("CR-A1", "CUST-1001", CardType.DEBIT);
        approved.setStatus(CardRequestStatus.APPROVED);
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.APPROVED)).thenReturn(List.of(approved));
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.PENDING)).thenReturn(List.of());

        cardRequestService.advanceRequestStatuses();

        assertThat(approved.getStatus()).isEqualTo(
                CardRequestStatus.ISSUED);
    }

    /**
     * Regression pin for the same-tick cascade bug found during manual
     * testing: a request newly approved in this run must not also be
     * issued in this same run. Since mocked repositories cannot reproduce
     * Hibernate's auto-flush behaviour (see the integration test for
     * that), this test instead pins the fix at the level that is actually
     * deterministic under mocks: the APPROVED-to-ISSUED query must run
     * before the PENDING query, so a request this method is about to
     * approve was never in the APPROVED batch it already processed.
     */
    @Test
    void advanceRequestStatuses_queriesApprovedBeforePending() {
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.APPROVED)).thenReturn(List.of());
        when(cardRequestRepository.findByStatus(
                CardRequestStatus.PENDING)).thenReturn(List.of());

        cardRequestService.advanceRequestStatuses();

        InOrder inOrder = inOrder(cardRequestRepository);
        inOrder.verify(cardRequestRepository)
                .findByStatus(CardRequestStatus.APPROVED);
        inOrder.verify(cardRequestRepository)
                .findByStatus(CardRequestStatus.PENDING);
    }
}
