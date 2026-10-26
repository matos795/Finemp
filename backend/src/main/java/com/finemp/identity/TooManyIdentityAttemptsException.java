package com.finemp.identity;

public class TooManyIdentityAttemptsException extends RuntimeException {
    public TooManyIdentityAttemptsException() {
        super("Muitas tentativas. Aguarde e tente novamente.");
    }
}
