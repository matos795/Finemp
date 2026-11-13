package com.finemp.debt;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebtInstallmentResponse(
        UUID id,
        int installmentNumber,
        LocalDate dueDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amortizationAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interestAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal installmentAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceAfter) {

    static DebtInstallmentResponse from(DebtInstallment installment) {
        return new DebtInstallmentResponse(
                installment.getId(),
                installment.getInstallmentNumber(),
                installment.getDueDate(),
                installment.getAmortizationAmount(),
                installment.getInterestAmount(),
                installment.getInstallmentAmount(),
                installment.getBalanceAfter());
    }
}
