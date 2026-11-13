export interface CashVsDreCash {
    realizedIncome: string
    realizedExpense: string
    netCashGeneration: string
}

export interface CashVsDreOperationalDre {
    netResult: string
}

export interface CashVsDreReport {
    companyId: string
    month: string
    partial: boolean
    coverage: string[]
    cash: CashVsDreCash
    operationalDre: CashVsDreOperationalDre
    difference: string
}
