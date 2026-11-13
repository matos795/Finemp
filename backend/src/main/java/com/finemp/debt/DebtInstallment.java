package com.finemp.debt;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "debt_installments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_debt_installment_number",
                columnNames = {"debt_agreement_id", "installment_number"}))
public class DebtInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_agreement_id", nullable = false, updatable = false)
    private DebtAgreement agreement;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amortization_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amortizationAmount;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "installment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    protected DebtInstallment() {
    }

    DebtInstallment(
            DebtAgreement agreement,
            DebtScheduleCalculator.Installment source) {
        if (agreement == null || source == null) {
            throw new IllegalArgumentException("Parcela da dívida inválida.");
        }
        this.agreement = agreement;
        this.installmentNumber = source.number();
        this.dueDate = source.dueDate();
        this.amortizationAmount = source.amortization();
        this.interestAmount = source.interest();
        this.installmentAmount = source.payment();
        this.balanceAfter = source.balance();
    }

    public UUID getId() { return id; }
    public int getInstallmentNumber() { return installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmortizationAmount() { return amortizationAmount; }
    public BigDecimal getInterestAmount() { return interestAmount; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
}
