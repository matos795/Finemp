package com.finemp.administration.firmaccess;

public class FirmAccessConflictException extends RuntimeException {
    public FirmAccessConflictException() {
        super("Revogue explicitamente os grants de empresa antes de remover o vínculo da contabilidade.");
    }
}
