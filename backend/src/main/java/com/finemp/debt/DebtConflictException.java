package com.finemp.debt;

public class DebtConflictException extends RuntimeException {
    public DebtConflictException() {
        super("O acordo de dívida foi alterado por outra operação.");
    }
}
