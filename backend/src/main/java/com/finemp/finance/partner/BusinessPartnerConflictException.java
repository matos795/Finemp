package com.finemp.finance.partner;

public class BusinessPartnerConflictException extends RuntimeException {
    public enum Reason { STALE_VERSION, DUPLICATE_DOCUMENT, TRANSACTION_NOT_SUPPORTED }
    private final Reason reason;
    public BusinessPartnerConflictException(Reason reason) {super("Conflito no cadastro do parceiro.");this.reason=reason;}
    public Reason reason() {return reason;}
}
