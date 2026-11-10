import type {
    FinancialDirection,
    FinancialEntryStatus,
} from "../types/financial-entry.types"

/**
 * Situação devolvida pelo backend (statusBadge do protótipo). "Vence hoje"
 * não é derivado no React: o contrato só traz OPEN, PAID e OVERDUE.
 */
export function FinancialEntryStatusBadge({
    status,
    direction,
}: {
    status: FinancialEntryStatus
    direction: FinancialDirection
}) {
    if (status === "PAID") {
        return <span className="badge b-ok">{direction === "INCOME" ? "Recebido" : "Pago"}</span>
    }

    if (status === "OVERDUE") {
        return <span className="badge b-bad">Vencido</span>
    }

    return <span className="badge b-neu">Em aberto</span>
}
