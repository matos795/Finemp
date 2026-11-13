package com.finemp.sales;

public class SalesRelationshipConflictException extends RuntimeException {

    public SalesRelationshipConflictException() {
        super("A oportunidade possui follow-up vinculado a outro cliente.");
    }
}
