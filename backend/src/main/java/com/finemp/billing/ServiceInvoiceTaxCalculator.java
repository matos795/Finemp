package com.finemp.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ServiceInvoiceTaxCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private ServiceInvoiceTaxCalculator() {
    }

    public static Result calculate(
            BigDecimal grossAmount,
            BigDecimal issRate,
            boolean issWithheld,
            BigDecimal irRate,
            BigDecimal pisRate,
            BigDecimal cofinsRate,
            BigDecimal csllRate,
            BigDecimal inssRate) {

        BigDecimal gross = money(grossAmount);
        BigDecimal iss = issWithheld ? tax(gross, rate(issRate)) : zero();
        BigDecimal ir = tax(gross, rate(irRate));
        BigDecimal pis = tax(gross, rate(pisRate));
        BigDecimal cofins = tax(gross, rate(cofinsRate));
        BigDecimal csll = tax(gross, rate(csllRate));
        BigDecimal inss = tax(gross, rate(inssRate));

        BigDecimal total = iss
                .add(ir)
                .add(pis)
                .add(cofins)
                .add(csll)
                .add(inss)
                .setScale(2, RoundingMode.UNNECESSARY);

        return new Result(
                gross,
                iss,
                ir,
                pis,
                cofins,
                csll,
                inss,
                total,
                gross.subtract(total).setScale(2, RoundingMode.UNNECESSARY));
    }

    private static BigDecimal tax(BigDecimal gross, BigDecimal percentage) {
        return gross
                .multiply(percentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Alíquota de retenção inválida.");
        }
        var normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        if (normalized.scale() > 4) {
            throw new IllegalArgumentException("Alíquota de retenção inválida.");
        }
        return normalized;
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Valor bruto inválido.");
        }
        try {
            var normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
            if (normalized.scale() > 2 || (long) normalized.precision() - normalized.scale() > 17) {
                throw new IllegalArgumentException("Valor bruto inválido.");
            }
            return normalized.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Valor bruto inválido.", exception);
        }
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public record Result(
            BigDecimal grossAmount,
            BigDecimal iss,
            BigDecimal ir,
            BigDecimal pis,
            BigDecimal cofins,
            BigDecimal csll,
            BigDecimal inss,
            BigDecimal totalWithheld,
            BigDecimal netAmount) {
    }
}
