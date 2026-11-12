export type FinancialCategoryKind = "GROUP" | "SUBCATEGORY"
export type FinancialCategoryDirection = "INCOME" | "EXPENSE"
export type CashActivity = "OPERATING" | "INVESTING" | "FINANCING"
export type CostBehavior = "FIXED" | "VARIABLE"
export type OperationalDreLine =
    | "REC" | "DED" | "CUS" | "DCOM" | "DADM" | "DPES" | "DTRI"
    | "OUT" | "DEP" | "RFIN" | "DFIN" | "IR" | "NAO"

/**
 * Categoria como o backend devolve. `cashActivity`, `dreLine` e
 * `costBehavior` são os valores próprios (raw; null = sem valor próprio);
 * `effective` é a classificação já resolvida pelo servidor (herança do
 * grupo e padrões). O código é gerado pelo backend.
 */
export interface FinancialCategory {
    id: string
    companyId: string
    parentId: string | null
    kind: FinancialCategoryKind
    direction: FinancialCategoryDirection
    code: string
    name: string
    cashActivity: CashActivity | null
    dreLine: OperationalDreLine | null
    costBehavior: CostBehavior | null
    effective: {
        cashActivity: CashActivity
        dreLine: OperationalDreLine
        costBehavior: CostBehavior
    }
    version: number
}

/** Filtros aplicados no servidor (GET …/financial-categories). */
export interface FinancialCategoryFilter {
    search: string
    direction: FinancialCategoryDirection | ""
    kind: FinancialCategoryKind | ""
}

/** Valores próprios enviados no PUT de classificação (somente MANAGE). */
export interface FinancialCategoryClassification {
    cashActivity: CashActivity | null
    dreLine: OperationalDreLine | null
    costBehavior: CostBehavior | null
}
