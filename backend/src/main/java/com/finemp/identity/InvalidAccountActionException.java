package com.finemp.identity;

public class InvalidAccountActionException extends RuntimeException {

    public InvalidAccountActionException() {
        super("Account action link is invalid or expired.");
    }
}
