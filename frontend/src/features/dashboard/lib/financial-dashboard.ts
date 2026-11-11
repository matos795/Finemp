import { ApiError } from "../../../lib/api/api-client.ts"
import type {
    CashAmounts,
    PresentationSnapshot,
    Ratio,
    SnapshotQuery,
} from "../../../lib/api/presentation-client.ts"
import type { CompanyPermission } from "../../auth/types/auth.types.ts"

/**
 * Parâmetros explícitos do snapshot para o Painel. O mês de análise começa
 * no mês atual, como no protótipo (pMes, data-padrao = hoje); a data-base
 * dos títulos em aberto é hoje e o escopo é ALL (todos os títulos sem
 * baixa), também como o painel homologado. Nada disso é inferido no backend.
 */
export function dashboardQuery(
    today: string,
    month = today.slice(0, 7),
): SnapshotQuery {
    return { month, asOf: today, scope: "ALL" }
}

/** Totais do mês escolhido, como vieram em cashYear.months (sem somar dias). */
export function dashboardMonthCash(
    snapshot: Pick<PresentationSnapshot, "month" | "cashYear">,
): (CashAmounts & { month: number }) | null {
    const monthNumber = Number(snapshot.month.slice(5, 7))

    return snapshot.cashYear.months.find((item) => item.month === monthNumber) ?? null
}

/**
 * Percentual já calculado pelo backend (unidade PERCENT, uma casa), só
 * trocando o separador decimal. Base indisponível fica sem valor (null),
 * nunca zero.
 */
export function formatIndicatorPercent(
    ratio: Ratio | undefined,
): string | null {
    if (!ratio || ratio.availability !== "AVAILABLE" || ratio.percent === null) {
        return null
    }

    return `${ratio.percent.replace(".", ",")}%`
}

interface OverdueBlock {
    count: number
    amount: string
}

/** Vencidos de pagar e receber, pelos totais do aging do backend. */
export function overdueSummary(
    snapshot: Pick<PresentationSnapshot, "payables" | "receivables">,
): { payables: OverdueBlock | null, receivables: OverdueBlock | null } | null {
    const block = (count: number, amount: string) =>
        count > 0 ? { count, amount } : null

    const payables = block(snapshot.payables.overdueCount, snapshot.payables.overdueAmount)
    const receivables = block(snapshot.receivables.overdueCount, snapshot.receivables.overdueAmount)

    return payables || receivables ? { payables, receivables } : null
}

/**
 * O snapshot exige FINANCIAL_ENTRY_VIEW ou _MANAGE. Sem elas o Painel não
 * consulta a API. O papel (CLIENT, ACCOUNTANT…) não entra na decisão.
 */
export function canSeeFinancialDashboard(
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return hasPermission("FINANCIAL_ENTRY_VIEW") || hasPermission("FINANCIAL_ENTRY_MANAGE")
}

/** Mensagem neutra; erro nunca vira painel zerado. */
export function describeDashboardError(error: unknown) {
    if (error instanceof ApiError) {
        switch (error.status) {
            case 400:
                return "Confira o mês de análise."
            case 403:
                return "Você não tem acesso aos números financeiros desta empresa."
            case 404:
                return "O painel financeiro não está disponível neste ambiente."
        }
    }

    return "Não foi possível carregar o painel. Tente de novo em instantes."
}
