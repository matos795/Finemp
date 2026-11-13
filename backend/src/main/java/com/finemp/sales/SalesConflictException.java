package com.finemp.sales;

public class SalesConflictException extends RuntimeException {
    public SalesConflictException() {
        super("O registro comercial foi alterado por outro usuário.");
    }
}
