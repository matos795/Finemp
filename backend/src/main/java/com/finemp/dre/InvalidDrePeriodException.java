package com.finemp.dre;

public class InvalidDrePeriodException extends IllegalArgumentException {
    public InvalidDrePeriodException() {
        super("Invalid period: startDate must not be after endDate");
    }
}
