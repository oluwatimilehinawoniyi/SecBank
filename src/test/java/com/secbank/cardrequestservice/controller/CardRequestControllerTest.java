package com.secbank.cardrequestservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secbank.cardrequestservice.domain.CardType;
import com.secbank.cardrequestservice.dto.CardRequestCreateRequest;
import com.secbank.cardrequestservice.service.CardRequestStatusScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CardRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CardRequestStatusScheduler cardRequestStatusScheduler;

    @Test
    void create_returnsCreatedWithLocationAndPendingStatus()
            throws Exception {
        String body = objectMapper.writeValueAsString(
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT));

        mockMvc.perform(post("/api/v1/card-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.reference").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void create_returns422ForUnknownCustomer() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CardRequestCreateRequest("CUST-DOES-NOT-EXIST",
                        CardType.DEBIT));

        mockMvc.perform(post("/api/v1/card-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_returns400ForBlankCustomerId() throws Exception {
        String body = "{\"customerId\": \"\", \"cardType\": \"DEBIT\"}";

        mockMvc.perform(post("/api/v1/card-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_sameIdempotencyKeyDifferentBody_returns409()
            throws Exception {
        String idempotencyKey = "test-key-controller-1";
        String firstBody = objectMapper.writeValueAsString(
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT));
        String secondBody = objectMapper.writeValueAsString(
                new CardRequestCreateRequest("CUST-1001",
                        CardType.CREDIT));

        mockMvc.perform(post("/api/v1/card-requests")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/card-requests")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondBody))
                .andExpect(status().isConflict());
    }

    @Test
    void getStatus_returns404ForUnknownReference() throws Exception {
        mockMvc.perform(get("/api/v1/card-requests/CR-DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatus_returnsCreatedRequestsStatus() throws Exception {
        String body = objectMapper.writeValueAsString(
                new CardRequestCreateRequest("CUST-1001", CardType.DEBIT));

        String responseJson = mockMvc.perform(post("/api/v1/card-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reference =
                objectMapper.readTree(responseJson).get("reference")
                        .asText();

        mockMvc.perform(get("/api/v1/card-requests/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("CUST-1001"));
    }
}
