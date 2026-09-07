package com.secbank.cardrequestservice.exception;

public class InvalidCardRequestTransitionException extends RuntimeException {

    public InvalidCardRequestTransitionException(String message) {
        super(message);
    }
}
