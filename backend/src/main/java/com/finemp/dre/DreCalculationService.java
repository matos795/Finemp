package com.finemp.dre;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DreCalculationService {

    private final DreMovementRepository dreMovementRepository;
    private final DreMovementValueCalculator valueCalculator;

    public DreCalculationService(
            DreMovementRepository dreMovementRepository,
            DreMovementValueCalculator valueCalculator) {
        this.dreMovementRepository = dreMovementRepository;
        this.valueCalculator = valueCalculator;
    }

    public DreCalculationResult calculate(
            UUID companyId,
            LocalDate startDate,
            LocalDate endDate) {

        validatePeriod(startDate, endDate);

        List<DreMovementRow> movements = dreMovementRepository.findAllByCompanyAndPeriod(
                companyId,
                startDate,
                endDate);

        Map<DreClassificationKey, BigDecimal> totals = new LinkedHashMap<>();

        Set<UUID> unclassifiedAccountIds = new HashSet<>();

        for (DreMovementRow movement : movements) {

            if (!hasCompleteClassification(movement)) {
                unclassifiedAccountIds.add(
                        movement.accountId());

                continue;
            }

            BigDecimal signedValue = valueCalculator.calculate(
                    movement.accountKey(),
                    movement.side(),
                    movement.amount());

            DreClassificationKey key = new DreClassificationKey(
                    movement.dreClassification(),
                    movement.dreOpenClassification(),
                    movement.ebitdaClassification());

            totals.merge(
                    key,
                    signedValue,
                    BigDecimal::add);
        }

        List<DreLine> lines = new ArrayList<>();

        totals.forEach((key, total) -> lines.add(
                new DreLine(
                        key.dreClassification(),
                        key.dreOpenClassification(),
                        key.ebitdaClassification(),
                        total)));

        return new DreCalculationResult(
                lines,
                unclassifiedAccountIds.size());
    }

    private boolean hasCompleteClassification(
            DreMovementRow movement) {

        return hasText(movement.dreClassification())
                && hasText(movement.dreOpenClassification())
                && hasText(movement.ebitdaClassification());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DreClassificationKey(
            String dreClassification,
            String dreOpenClassification,
            String ebitdaClassification) {
    }

    private void validatePeriod(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Invalid period: startDate must not be after endDate");
        }
    }
}