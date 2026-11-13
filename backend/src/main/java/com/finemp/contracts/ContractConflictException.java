package com.finemp.contracts;

public class ContractConflictException extends RuntimeException {

    public enum Reason {
        STALE_VERSION,
        REFERENCED_PARTNER_CHANGE
    }

    private final Reason reason;

    public ContractConflictException() {
        this(Reason.STALE_VERSION);
    }

    public ContractConflictException(Reason reason) {
        super(message(reason));
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static String message(Reason reason) {
        return switch (reason) {
            case STALE_VERSION ->
                    "O contrato foi alterado por outro usuário. Atualize a tela e tente novamente.";
            case REFERENCED_PARTNER_CHANGE ->
                    "O parceiro do contrato não pode ser alterado enquanto houver nota fiscal vinculada.";
        };
    }
}
