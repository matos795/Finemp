package com.finemp.finance.reporting;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

public final class WorkingCapitalCycleCalculator {

    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final int INTERNAL_SCALE = 12;

    private WorkingCapitalCycleCalculator() { }

    public static Report calculate(
            LocalDate from,
            LocalDate to,
            LocalDate today,
            int pmeDays,
            List<Row> source) {

        if (from == null || to == null || today == null || to.isBefore(from) || pmeDays < 0) {
            throw new IllegalArgumentException("Parâmetros do ciclo financeiro inválidos.");
        }

        List<Row> rows = source == null ? List.of() : List.copyOf(source);
        int daysInPeriod = Math.max(1, Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1));

        SideCalculation receivables = side(rows, Side.RECEIVABLE, from, to, today, daysInPeriod);
        SideCalculation payables = side(rows, Side.PAYABLE, from, to, today, daysInPeriod);

        BigDecimal pmrRaw = first(receivables.realDaysRaw(), receivables.grantedDaysRaw());
        BigDecimal pmpRaw = first(payables.realDaysRaw(), payables.grantedDaysRaw());
        BigDecimal pmeRaw = BigDecimal.valueOf(pmeDays);

        BigDecimal operatingRaw = pmrRaw == null ? null : pmeRaw.add(pmrRaw, MATH);
        BigDecimal financialRaw = pmrRaw == null || pmpRaw == null
                ? null
                : pmeRaw.add(pmrRaw, MATH).subtract(pmpRaw, MATH);
        BigDecimal workingCapitalRaw = financialRaw == null
                ? null
                : receivables.dailyRaw().multiply(financialRaw, MATH);

        return new Report(
                from,
                to,
                today,
                daysInPeriod,
                pmeDays,
                receivables.response(),
                payables.response(),
                decimal(pmrRaw),
                decimal(pmpRaw),
                decimal(operatingRaw),
                decimal(financialRaw),
                money(workingCapitalRaw));
    }

    private static SideCalculation side(
            List<Row> rows,
            Side side,
            LocalDate from,
            LocalDate to,
            LocalDate today,
            int daysInPeriod) {

        List<Row> matching = rows.stream()
                .filter(row -> row.side() == side)
                .toList();
        List<Row> issued = matching.stream()
                .filter(row -> inside(row.documentDate(), from, to))
                .toList();
        List<Row> paid = matching.stream()
                .filter(row -> row.paymentDate() != null && inside(row.paymentDate(), from, to))
                .toList();
        List<Row> open = matching.stream()
                .filter(row -> row.paymentDate() == null || row.paymentDate().isAfter(today))
                .toList();
        List<Row> overdue = open.stream()
                .filter(row -> row.dueDate().isBefore(today))
                .toList();

        BigDecimal volume = sum(issued, Row::nominalAmount);
        BigDecimal dailyRaw = volume.divide(BigDecimal.valueOf(daysInPeriod), INTERNAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal grantedRaw = weighted(
                issued,
                row -> days(row.documentDate(), row.dueDate()),
                Row::nominalAmount);
        BigDecimal realRaw = weighted(
                paid,
                row -> days(row.documentDate(), row.paymentDate()),
                Row::effectiveAmount);
        BigDecimal delayRaw = weighted(
                paid,
                row -> Math.max(0, days(row.dueDate(), row.paymentDate())),
                Row::effectiveAmount);

        BigDecimal paidAmount = sum(paid, Row::effectiveAmount);
        BigDecimal punctualAmount = sum(
                paid.stream().filter(row -> !row.paymentDate().isAfter(row.dueDate())).toList(),
                Row::effectiveAmount);
        BigDecimal punctuality = paidAmount.signum() <= 0
                ? null
                : punctualAmount.multiply(BigDecimal.valueOf(100), MATH)
                        .divide(paidAmount, INTERNAL_SCALE, RoundingMode.HALF_UP);

        List<Row> early = paid.stream()
                .filter(row -> days(row.paymentDate(), row.dueDate()) > 2)
                .toList();
        BigDecimal earlyDays = weighted(
                early,
                row -> days(row.paymentDate(), row.dueDate()),
                Row::effectiveAmount);

        BigDecimal interest = paid.stream()
                .map(row -> row.effectiveAmount().subtract(row.nominalAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounts = paid.stream()
                .map(row -> row.nominalAmount().subtract(row.effectiveAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cashAmount = sum(
                issued.stream().filter(row -> days(row.documentDate(), row.dueDate()) <= 0).toList(),
                Row::nominalAmount);
        BigDecimal openAmount = sum(open, Row::nominalAmount);
        BigDecimal overdueAmount = sum(overdue, Row::nominalAmount);
        BigDecimal overduePercent = openAmount.signum() <= 0
                ? null
                : overdueAmount.multiply(BigDecimal.valueOf(100), MATH)
                        .divide(openAmount, INTERNAL_SCALE, RoundingMode.HALF_UP);
        BigDecimal openDelayRaw = weighted(
                overdue,
                row -> days(row.dueDate(), today),
                Row::nominalAmount);
        BigDecimal balanceDaysRaw = dailyRaw.signum() <= 0
                ? null
                : openAmount.divide(dailyRaw, INTERNAL_SCALE, RoundingMode.HALF_UP);

        List<TermBandTotal> bands = Arrays.stream(TermBand.values())
                .map(band -> {
                    List<Row> selected = issued.stream()
                            .filter(row -> band.matches(days(row.documentDate(), row.dueDate())))
                            .toList();
                    return new TermBandTotal(
                            band,
                            selected.size(),
                            money(sum(selected, Row::nominalAmount)));
                })
                .toList();

        List<PartnerTermMetrics> partners = partnerMetrics(matching, issued, paid, overdue);

        SideMetrics response = new SideMetrics(
                money(volume),
                money(dailyRaw),
                issued.size(),
                decimal(grantedRaw),
                decimal(realRaw),
                decimal(delayRaw),
                decimal(punctuality),
                new EarlyPaymentMetrics(
                        early.size(),
                        money(sum(early, Row::effectiveAmount)),
                        decimal(earlyDays)),
                money(interest),
                money(discounts),
                money(cashAmount),
                money(openAmount),
                money(overdueAmount),
                decimal(overduePercent),
                decimal(openDelayRaw),
                decimal(balanceDaysRaw),
                bands,
                partners);

        return new SideCalculation(response, dailyRaw, grantedRaw, realRaw);
    }

    private static List<PartnerTermMetrics> partnerMetrics(
            List<Row> matching,
            List<Row> issued,
            List<Row> paid,
            List<Row> overdue) {

        Map<String, PartnerBucket> buckets = new HashMap<>();

        for (Row row : matching) {
            String key = row.partnerId() == null ? "" : row.partnerId();
            buckets.computeIfAbsent(
                    key,
                    ignored -> new PartnerBucket(row.partnerId(), displayPartner(row)));
        }

        for (Row row : issued) {
            PartnerBucket bucket = bucket(buckets, row);
            bucket.issued.add(row);
        }
        for (Row row : paid) {
            PartnerBucket bucket = bucket(buckets, row);
            bucket.paid.add(row);
        }
        for (Row row : overdue) {
            PartnerBucket bucket = bucket(buckets, row);
            bucket.overdue.add(row);
        }

        return buckets.values().stream()
                .map(bucket -> {
                    BigDecimal volume = sum(bucket.issued, Row::nominalAmount);
                    BigDecimal overdueOpen = sum(bucket.overdue, Row::nominalAmount);
                    return new PartnerTermMetrics(
                            bucket.partnerId,
                            bucket.partnerName,
                            money(volume),
                            decimal(weighted(
                                    bucket.issued,
                                    row -> days(row.documentDate(), row.dueDate()),
                                    Row::nominalAmount)),
                            decimal(weighted(
                                    bucket.paid,
                                    row -> days(row.documentDate(), row.paymentDate()),
                                    Row::effectiveAmount)),
                            decimal(weighted(
                                    bucket.paid,
                                    row -> Math.max(0, days(row.dueDate(), row.paymentDate())),
                                    Row::effectiveAmount)),
                            money(overdueOpen),
                            volume.add(overdueOpen));
                })
                .sorted(Comparator
                        .comparing(PartnerTermMetrics::sortWeight)
                        .reversed()
                        .thenComparing(PartnerTermMetrics::partnerName, String.CASE_INSENSITIVE_ORDER))
                .limit(10)
                .map(PartnerTermMetrics::withoutSortWeight)
                .toList();
    }

    private static PartnerBucket bucket(Map<String, PartnerBucket> buckets, Row row) {
        String key = row.partnerId() == null ? "" : row.partnerId();
        return buckets.computeIfAbsent(
                key,
                ignored -> new PartnerBucket(row.partnerId(), displayPartner(row)));
    }

    private static String displayPartner(Row row) {
        if (row.partnerName() == null || row.partnerName().isBlank()) return "Sem cadastro";
        return row.partnerName().trim();
    }

    private static boolean inside(LocalDate value, LocalDate from, LocalDate to) {
        return value != null && !value.isBefore(from) && !value.isAfter(to);
    }

    private static int days(LocalDate from, LocalDate to) {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to));
    }

    private static BigDecimal weighted(
            List<Row> rows,
            java.util.function.ToIntFunction<Row> days,
            java.util.function.Function<Row, BigDecimal> weight) {
        BigDecimal denominator = BigDecimal.ZERO;
        BigDecimal numerator = BigDecimal.ZERO;
        for (Row row : rows) {
            BigDecimal rowWeight = weight.apply(row);
            if (rowWeight == null || rowWeight.signum() <= 0) continue;
            denominator = denominator.add(rowWeight);
            numerator = numerator.add(
                    rowWeight.multiply(BigDecimal.valueOf(days.applyAsInt(row)), MATH));
        }
        if (denominator.signum() <= 0) return null;
        return numerator.divide(denominator, INTERNAL_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(
            List<Row> rows,
            java.util.function.Function<Row, BigDecimal> amount) {
        return rows.stream()
                .map(amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal first(BigDecimal preferred, BigDecimal fallback) {
        return preferred == null ? fallback : preferred;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public enum Side { RECEIVABLE, PAYABLE }

    public enum TermBand {
        CASH("À vista") {
            @Override boolean matches(int days) { return days <= 0; }
        },
        DAYS_1_30("1 a 30 dias") {
            @Override boolean matches(int days) { return days >= 1 && days <= 30; }
        },
        DAYS_31_60("31 a 60 dias") {
            @Override boolean matches(int days) { return days >= 31 && days <= 60; }
        },
        DAYS_61_90("61 a 90 dias") {
            @Override boolean matches(int days) { return days >= 61 && days <= 90; }
        },
        OVER_90("Acima de 90 dias") {
            @Override boolean matches(int days) { return days > 90; }
        };

        private final String label;
        TermBand(String label) { this.label = label; }
        public String label() { return label; }
        abstract boolean matches(int days);
    }

    public record Row(
            Side side,
            String partnerId,
            String partnerName,
            BigDecimal nominalAmount,
            BigDecimal effectiveAmount,
            LocalDate documentDate,
            LocalDate dueDate,
            LocalDate paymentDate) {

        public Row {
            Objects.requireNonNull(side);
            Objects.requireNonNull(nominalAmount);
            Objects.requireNonNull(effectiveAmount);
            Objects.requireNonNull(documentDate);
            Objects.requireNonNull(dueDate);
            if (nominalAmount.signum() < 0 || effectiveAmount.signum() < 0) {
                throw new IllegalArgumentException("Linha do ciclo financeiro inválida.");
            }
        }
    }

    public record Report(
            LocalDate from,
            LocalDate to,
            LocalDate asOf,
            int daysInPeriod,
            int pmeDays,
            SideMetrics receivables,
            SideMetrics payables,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pmrDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal pmpDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal operatingCycleDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal financialCycleDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal workingCapitalForCycle) {
    }

    public record SideMetrics(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dailyVolume,
            long issuedCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grantedDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal realDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageDelayDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal punctualityPercent,
            EarlyPaymentMetrics earlyPayments,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal interest,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discounts,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cashAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueAmount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overduePercent,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal openDelayDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balanceDays,
            List<TermBandTotal> bands,
            List<PartnerTermMetrics> partners) {

        public SideMetrics {
            bands = List.copyOf(bands);
            partners = List.copyOf(partners);
        }
    }

    public record EarlyPaymentMetrics(
            long count,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageDays) {
    }

    public record TermBandTotal(
            TermBand band,
            long count,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
    }

    public record PartnerTermMetrics(
            String partnerId,
            String partnerName,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal volume,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal grantedDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal realDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal averageDelayDays,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal overdueOpenAmount,
            @com.fasterxml.jackson.annotation.JsonIgnore BigDecimal sortWeight) {

        PartnerTermMetrics withoutSortWeight() {
            return new PartnerTermMetrics(
                    partnerId,
                    partnerName,
                    volume,
                    grantedDays,
                    realDays,
                    averageDelayDays,
                    overdueOpenAmount,
                    BigDecimal.ZERO);
        }
    }

    private record SideCalculation(
            SideMetrics response,
            BigDecimal dailyRaw,
            BigDecimal grantedDaysRaw,
            BigDecimal realDaysRaw) {
    }

    private static final class PartnerBucket {
        private final String partnerId;
        private final String partnerName;
        private final List<Row> issued = new ArrayList<>();
        private final List<Row> paid = new ArrayList<>();
        private final List<Row> overdue = new ArrayList<>();

        private PartnerBucket(String partnerId, String partnerName) {
            this.partnerId = partnerId;
            this.partnerName = partnerName;
        }
    }
}
