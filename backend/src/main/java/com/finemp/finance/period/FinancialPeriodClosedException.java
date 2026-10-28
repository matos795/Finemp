package com.finemp.finance.period;

public class FinancialPeriodClosedException extends RuntimeException {
    public FinancialPeriodClosedException() {super("Um dos períodos financeiros afetados está fechado.");}
}
