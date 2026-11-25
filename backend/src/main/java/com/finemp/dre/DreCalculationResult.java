package com.finemp.dre;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DRE contábil da Company no período.
 *
 * <p>{@code lines} e {@code unclassifiedAccountCount} são o contrato original. {@code groups}
 * e {@code classifiedTotal} são as agregações autoritativas (#446): o cliente apresenta,
 * não soma. Valores agregados são strings decimais exatas na escala armazenada.
 * {@code coverage} traz fatos de cobertura contábil importada por fonte e dataset MOVEMENTS.
 */
public record DreCalculationResult(
        List<DreLine> lines,
        int unclassifiedAccountCount,
        List<DreGroup> groups,
        String classifiedTotal,
        List<SourceCoverage> coverage) {

    static final int AMOUNT_SCALE = 4;

    public DreCalculationResult {
        lines = List.copyOf(lines);
        groups = List.copyOf(groups);
        coverage = List.copyOf(coverage);
    }

    public DreCalculationResult(List<DreLine> lines, int unclassifiedAccountCount) {
        this(lines, unclassifiedAccountCount, List.of());
    }

    public DreCalculationResult(List<DreLine> lines, int unclassifiedAccountCount, List<SourceCoverage> coverage) {
        this(lines, unclassifiedAccountCount, group(lines), exact(sum(lines)), coverage);
    }

    public record DreGroup(String dreClassification, String total, List<DreOpenGroup> groups) {
        public DreGroup {
            groups = List.copyOf(groups);
        }
    }

    public record DreOpenGroup(String dreOpenClassification, String total, List<DreGroupLine> lines) {
        public DreOpenGroup {
            lines = List.copyOf(lines);
        }
    }

    public record DreGroupLine(String ebitdaClassification, String total) { }

    /** Cobertura cumulativa importada de MOVEMENTS da fonte; lacunas ficam explícitas em intervals. */
    public record SourceCoverage(
            UUID accountingSourceId,
            LocalDate importedFrom,
            LocalDate importedTo,
            List<CoverageInterval> intervals) {
        public SourceCoverage {
            intervals = List.copyOf(intervals);
        }
    }

    public record CoverageInterval(LocalDate importedFrom, LocalDate importedTo) { }

    /** Agrupa preservando a ordem das linhas recebidas. */
    static List<DreGroup> group(List<DreLine> lines) {
        Map<String, Map<String, List<DreLine>>> tree = new LinkedHashMap<>();
        for (DreLine line : lines) {
            tree.computeIfAbsent(line.dreClassification(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(line.dreOpenClassification(), key -> new ArrayList<>())
                    .add(line);
        }
        List<DreGroup> result = new ArrayList<>();
        tree.forEach((dre, openGroups) -> {
            List<DreOpenGroup> open = new ArrayList<>();
            List<DreLine> all = new ArrayList<>();
            openGroups.forEach((openName, groupLines) -> {
                all.addAll(groupLines);
                open.add(new DreOpenGroup(openName, exact(sum(groupLines)),
                        groupLines.stream().map(l -> new DreGroupLine(l.ebitdaClassification(), exact(l.total()))).toList()));
            });
            result.add(new DreGroup(dre, exact(sum(all)), open));
        });
        return result;
    }

    private static BigDecimal sum(List<DreLine> lines) {
        return lines.stream().map(DreLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String exact(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }
}
