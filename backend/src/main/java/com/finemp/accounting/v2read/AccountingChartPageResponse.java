package com.finemp.accounting.v2read;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Plano de contas completo da origem (#474). Só é FULL_CHART com cobertura snapshot
 * de CHART publicada; sem ela a resposta é IMPORTED_ACCOUNTS_ONLY e vazia, e o universo
 * importado/observado continua em {@code /accounting-accounts}.
 */
public record AccountingChartPageResponse(
        UUID accountingSourceId,
        Availability availability,
        ChartCoverage coverage,
        List<Item> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public enum Availability { FULL_CHART, IMPORTED_ACCOUNTS_ONLY }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChartCoverage(String dataset, Instant publishedAt) {}

    public record Item(
            UUID accountId,
            String sourceAccountId,
            String classificationCode,
            String description,
            SourceCodes sourceCodes) {

        static Item from(AccountingChartRow row) {
            return new Item(row.accountId(), row.sourceAccountId(), row.classificationCode(), row.description(),
                    new SourceCodes(row.accountType(), row.calculationType(), row.balanceNature(),
                            row.balanceControl(), row.showInBalanceSheet(), row.nativeDreCode(),
                            row.nativeDreAccount()));
        }
    }

    /** Códigos crus do plano na origem; nenhuma interpretação é derivada deles aqui. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SourceCodes(
            String accountType,
            String calculationType,
            String balanceNature,
            String balanceControl,
            String showInBalanceSheet,
            String nativeDreCode,
            String nativeDreAccount) {}

    public AccountingChartPageResponse {
        items = List.copyOf(items);
    }
}
