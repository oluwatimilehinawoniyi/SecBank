package com.secbank.cardrequestservice.exception;

public class CardRequestNotFoundException extends RuntimeException {

    public CardRequestNotFoundException(String message) {
        super(message);
    }
}
