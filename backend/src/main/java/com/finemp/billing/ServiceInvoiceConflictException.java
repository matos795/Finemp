package com.finemp.billing;

public class ServiceInvoiceConflictException extends RuntimeException {
    public ServiceInvoiceConflictException() {
        super("A nota fiscal foi alterada por outra operação.");
    }
}
