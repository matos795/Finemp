import type {
    CashAmounts,
    CashYear,
    DecimalText,
    PresentationSnapshot,
} from "../../../lib/api/presentation-client"
import type { PageResponse } from "../../../lib/api/pagination.types"

/**
 * Tipos do fluxo de caixa reaproveitados do cliente da Apresentação de
 * Resultados (mesmo contrato da API), sem redefinir campos.
 */
export type CashTotals = CashAmounts

/** getMonthlyCashTotals: meses, atividades, trimestres, semestres e totais. */
export type MonthlyCashTotals = CashYear

/** getDailyCashTotals: todos os dias do mês (YYYY-MM). */
export type DailyCashTotals = PresentationSnapshot["cashMonth"]

export type CashActivity = CashYear["activityMonths"][number]["activity"]

export type CashDirection = "INCOME" | "EXPENSE"

/** Realizado (pela baixa) ou em aberto (pelo vencimento). */
export type CashState = "REALIZED" | "OPEN"

/** Filtro comum ao detalhe por categoria e por título de um mês. */
export interface MonthlyCashDetailFilter {
    month: string
    direction: CashDirection
    state: CashState
    activity?: CashActivity
    categoryId?: string
}

/** Item de listMonthlyCashCategoryTotals: subtotal exato do servidor. */
export interface MonthlyCashCategoryTotal {
    categoryId: string
    categoryName: string | null
    categoryCode: string | null
    groupId: string | null
    groupName: string | null
    activity: CashActivity
    titleCount: number
    amount: DecimalText
}

/** Item de listMonthlyCashTitles. */
export interface MonthlyCashTitle {
    id: string
    description: string
    direction: CashDirection
    state: CashState
    activity: CashActivity
    cashDate: string
    amount: DecimalText
    nominalAmount: DecimalText
    dueDate: string
    paymentDate: string | null
    categoryId: string
    partnerId: string | null
    bankAccountId: string
}

export type MonthlyCashCategoryTotalsPage = PageResponse<MonthlyCashCategoryTotal>

export type MonthlyCashTitlesPage = PageResponse<MonthlyCashTitle>
