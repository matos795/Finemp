package com.finemp.accounting.movement;

public class InvalidAccountingPeriodException
        extends RuntimeException {

    public InvalidAccountingPeriodException() {
        super("Start date must not be after end date");
    }
}