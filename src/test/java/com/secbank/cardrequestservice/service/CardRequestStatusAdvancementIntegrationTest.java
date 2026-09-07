package com.secbank.cardrequestservice.service;

import com.secbank.cardrequestservice.domain.CardRequest;
import com.secbank.cardrequestservice.domain.CardRequestStatus;
import com.secbank.cardrequestservice.domain.CardType;
import com.secbank.cardrequestservice.domain.Customer;
import com.secbank.cardrequestservice.domain.RiskTier;
import com.secbank.cardrequestservice.repository.CardRequestRepository;
import com.secbank.cardrequestservice.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CardRequestStatusAdvancementIntegrationTest {

    @Autowired
    private CardRequestService cardRequestService;
    @Autowired
    private CardRequestRepository cardRequestRepository;
    @Autowired
    private CustomerRepository customerRepository;

    @MockitoBean
    private CardRequestStatusScheduler cardRequestStatusScheduler;

    @Test
    void advanceRequestStatuses_doesNotCascadeANewlyApprovedRequestToIssuedInTheSameRun() {
        Customer customer = customerRepository.save(
                new Customer("CUST-TEST-STD", "Test Standard Customer",
                        RiskTier.STANDARD));
        cardRequestRepository.save(
                new CardRequest("CR-INTEGRATION-01",
                        customer.getCustomerId(), CardType.DEBIT));

        cardRequestService.advanceRequestStatuses();

        CardRequest reloaded =
                cardRequestRepository.findByReference("CR-INTEGRATION-01")
                        .orElseThrow();
        assertThat(reloaded.getStatus())
                .as("a request approved during this run must not also be issued in the same run")
                .isEqualTo(CardRequestStatus.APPROVED);
    }

    @Test
    void advanceRequestStatuses_calledTwice_progressesPendingRequestAllTheWayToIssued() {
        Customer customer = customerRepository.save(
                new Customer("CUST-TEST-STD2", "Test Standard Customer 2",
                        RiskTier.STANDARD));
        cardRequestRepository.save(
                new CardRequest("CR-INTEGRATION-02",
                        customer.getCustomerId(), CardType.CREDIT));

        cardRequestService.advanceRequestStatuses();
        assertThat(
                cardRequestRepository.findByReference("CR-INTEGRATION-02")
                        .orElseThrow().getStatus())
                .isEqualTo(CardRequestStatus.APPROVED);

        cardRequestService.advanceRequestStatuses();
        assertThat(
                cardRequestRepository.findByReference("CR-INTEGRATION-02")
                        .orElseThrow().getStatus())
                .isEqualTo(CardRequestStatus.ISSUED);
    }

    @Test
    void advanceRequestStatuses_rejectsHighRiskCustomerRequestImmediately() {
        Customer customer = customerRepository.save(
                new Customer("CUST-TEST-HR", "Test High Risk Customer",
                        RiskTier.HIGH_RISK));
        cardRequestRepository.save(
                new CardRequest("CR-INTEGRATION-03",
                        customer.getCustomerId(), CardType.CREDIT));

        cardRequestService.advanceRequestStatuses();

        CardRequest reloaded =
                cardRequestRepository.findByReference("CR-INTEGRATION-03")
                        .orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(
                CardRequestStatus.REJECTED);
        assertThat(reloaded.getRejectionReason()).isNotBlank();
    }
}
