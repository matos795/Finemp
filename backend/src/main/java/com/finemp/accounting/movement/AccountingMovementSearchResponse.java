package com.finemp.accounting.movement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.finemp.accounting.canonical.persistence.AccountingEntrySourceMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Página de movimentos (#444). Valores como string decimal na escala armazenada
 * (NUMERIC(19,4)): o cliente não perde precisão e nada é arredondado.
 * {@code notSynchronized} lista o que o contrato canônico ainda não carrega.
 */
public record AccountingMovementSearchResponse(
        List<Item> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        Totals totals,
        List<String> notSynchronized) {

    static final int AMOUNT_SCALE = 4;
    static final List<String> NOT_SYNCHRONIZED =
            List.of("COUNTERPART", "DOCUMENT", "COST_CENTER", "CLOSING_ENTRY", "OPENING_BALANCE");

    public AccountingMovementSearchResponse {
        items = List.copyOf(items);
        notSynchronized = List.copyOf(notSynchronized);
    }

    /**
     * Campos legados inalterados; {@code sourceMetadataStatus} e {@code sourceMetadata}
     * são aditivos (#474). Sem metadado v2 gravado para (fonte, lançamento), o status é
     * NOT_SYNCHRONIZED e {@code sourceMetadata} é null; com metadado, o objeto traz todos
     * os campos, e um null ali significa "sincronizado e vazio na origem".
     */
    public record Item(UUID id, LocalDate accountingDate, UUID accountingSourceId, UUID accountId, String accountKey,
                       String accountDescription, AccountingMovementSide side, String amount, String history,
                       String sourceEntryKey, SourceMetadataStatus sourceMetadataStatus,
                       SourceMetadata sourceMetadata) {

        static Item from(AccountingMovement movement, AccountingEntrySourceMetadata metadata) {
            return new Item(movement.getId(), movement.getAccountingDate(), movement.getAccountingSourceId(),
                    movement.getAccount().getId(), movement.getAccount().getAccountKey(),
                    movement.getAccount().getDescription(), movement.getSide(), exact(movement.getAmount()),
                    movement.getHistory(), movement.getSourceEntryKey(),
                    metadata == null ? SourceMetadataStatus.NOT_SYNCHRONIZED : SourceMetadataStatus.SYNCHRONIZED,
                    metadata == null ? null : SourceMetadata.from(metadata));
        }
    }

    public enum SourceMetadataStatus { SYNCHRONIZED, NOT_SYNCHRONIZED }

    /** Códigos crus da origem, sem interpretação de negócio. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SourceMetadata(Instant sourceRecordedAt, String historyCode, String originCode,
                                 String entryTypeCode, String dataOriginCode, String batchCode,
                                 String documentNumber, String operationGroupKey, String transactionKey,
                                 String originEstablishmentCode) {

        static SourceMetadata from(AccountingEntrySourceMetadata metadata) {
            return new SourceMetadata(metadata.getSourceRecordedAt(), metadata.getHistoryCode(),
                    metadata.getOriginCode(), metadata.getEntryTypeCode(), metadata.getDataOriginCode(),
                    metadata.getBatchCode(), metadata.getDocumentNumber(), metadata.getOperationGroupKey(),
                    metadata.getTransactionKey(), metadata.getOriginEstablishmentCode());
        }
    }

    public record Totals(String debit, String credit) {
        static Totals from(AccountingMovementTotals totals) {
            return new Totals(exact(totals.debit()), exact(totals.credit()));
        }
    }

    static String exact(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }
}
