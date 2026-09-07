package com.secbank.cardrequestservice.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CardRequestStatusScheduler {

    private static final long FIXED_DELAY_MS = 15_000;

    private final CardRequestService cardRequestService;

    public CardRequestStatusScheduler(
            CardRequestService cardRequestService) {
        this.cardRequestService = cardRequestService;
    }

    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void advanceRequestStatuses() {
        cardRequestService.advanceRequestStatuses();
    }
}
