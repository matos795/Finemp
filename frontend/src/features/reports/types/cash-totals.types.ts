/** Totais em texto decimal exato, calculados no backend. */
export interface CashTotals {
    realIncome: string
    realExpense: string
    openIncome: string
    openExpense: string
}

/** getMonthlyCashTotals: 12 meses do ano informado. */
export interface MonthlyCashTotals {
    year: number
    months: (CashTotals & { month: number })[]
}

/** getDailyCashTotals: todos os dias do mês (YYYY-MM). */
export interface DailyCashTotals {
    month: string
    days: (CashTotals & { date: string })[]
}
