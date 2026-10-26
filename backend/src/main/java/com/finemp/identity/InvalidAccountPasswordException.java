package com.finemp.identity;

public class InvalidAccountPasswordException extends RuntimeException {

    public InvalidAccountPasswordException() {
        super("Password must contain at least eight characters, a letter and a digit, within the encoder limit.");
    }
}
