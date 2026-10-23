package com.finemp.accounting.classification.importing;

public class PlanoQReadException extends RuntimeException {

    public PlanoQReadException(String message) {
        super(message);
    }

    public PlanoQReadException(
            String message,
            Throwable cause) {

        super(message, cause);
    }
}