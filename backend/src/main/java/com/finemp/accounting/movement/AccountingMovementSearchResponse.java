package com.finemp.accounting.movement;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public record Item(UUID id, LocalDate accountingDate, UUID accountingSourceId, UUID accountId, String accountKey,
                       String accountDescription, AccountingMovementSide side, String amount, String history,
                       String sourceEntryKey) {

        static Item from(AccountingMovement movement) {
            return new Item(movement.getId(), movement.getAccountingDate(), movement.getAccountingSourceId(),
                    movement.getAccount().getId(), movement.getAccount().getAccountKey(),
                    movement.getAccount().getDescription(), movement.getSide(), exact(movement.getAmount()),
                    movement.getHistory(), movement.getSourceEntryKey());
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
