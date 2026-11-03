export type AgingDirection = "INCOME" | "EXPENSE"

export type AgingScope = "ALL" | "COMPETENCE"

export type AgingBandCode =
    | "DUE_0_30"
    | "DUE_31_60"
    | "DUE_61_90"
    | "DUE_OVER_90"
    | "OVERDUE_1_30"
    | "OVERDUE_31_60"
    | "OVERDUE_61_90"
    | "OVERDUE_OVER_90"

/** Faixa do aging; `amount` é texto decimal exato calculado no backend. */
export interface AgingBand {
    band: AgingBandCode
    count: number
    amount: string
}

/** Resposta de getOpenTitlesAging. */
export interface OpenTitlesAging {
    asOf: string
    direction: AgingDirection
    scope: AgingScope
    totalCount: number
    totalAmount: string
    bands: AgingBand[]
}
