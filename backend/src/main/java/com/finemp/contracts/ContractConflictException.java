package com.finemp.contracts;

public class ContractConflictException extends RuntimeException {
    public ContractConflictException() {
        super("O contrato foi alterado por outro usuário. Atualize a tela e tente novamente.");
    }
}
