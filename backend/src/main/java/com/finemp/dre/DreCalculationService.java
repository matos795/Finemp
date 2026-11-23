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

import org.springframework.stereotype.Service;

@Service
public class DreCalculationService {

    /** Ordem técnica estável; a ordem oficial da demonstração é PONTO ABERTO (O2/O21). */
    private static final java.util.Comparator<DreLine> LINE_ORDER = java.util.Comparator
            .comparing(DreLine::dreClassification)
            .thenComparing(DreLine::dreOpenClassification)
            .thenComparing(DreLine::ebitdaClassification);

    private final DreMovementRepository dreMovementRepository;
    private final DreMovementValueCalculator valueCalculator;
    private final DreCoverageReader coverageReader;

    @org.springframework.beans.factory.annotation.Autowired
    public DreCalculationService(
            DreMovementRepository dreMovementRepository,
            DreMovementValueCalculator valueCalculator,
            DreCoverageReader coverageReader) {
        this.dreMovementRepository = dreMovementRepository;
        this.valueCalculator = valueCalculator;
        this.coverageReader = coverageReader;
    }

    /** Sem leitura de cobertura (testes de cálculo puro). */
    public DreCalculationService(
            DreMovementRepository dreMovementRepository,
            DreMovementValueCalculator valueCalculator) {
        this(dreMovementRepository, valueCalculator, companyId -> List.of());
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

        lines.sort(LINE_ORDER);

        return new DreCalculationResult(
                lines,
                unclassifiedAccountIds.size(),
                coverageReader.coverage(companyId));
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