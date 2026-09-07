package com.secbank.cardrequestservice.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ReferenceGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int RANDOM_LENGTH = 10;
    private static final String PREFIX = "CR-";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder reference =
                new StringBuilder(PREFIX.length() + RANDOM_LENGTH);
        reference.append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            reference.append(
                    ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}
