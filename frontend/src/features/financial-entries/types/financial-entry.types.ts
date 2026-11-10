import type { PageResponse } from "../../../lib/api/pagination.types.ts"

/** Valor monetário em texto decimal exato, como no contrato ("1234.56"). */
export type DecimalText = string

export type FinancialDirection = "INCOME" | "EXPENSE"

/** Situação calculada pelo backend na data de referência (`asOfDate`). */
export type FinancialEntryStatus = "OPEN" | "PAID" | "OVERDUE"

export type CostBehavior = "FIXED" | "VARIABLE"

/** `FinancialEntryValues` da leitura (GET). */
export interface FinancialEntryValues {
    direction: FinancialDirection
    description: string
    nominalAmount: DecimalText
    documentDate: string
    entryDate: string
    competence: string
    dueDate: string
    paymentDate: string | null
    actualPaidAmount: DecimalText | null
    bankAccountId: string
    categoryId: string
    partnerId: string | null
    resultCenterId: string | null
    costBehaviorOverride: CostBehavior | null
    paymentMethod: string
    documentNumber: string
    notes: string
}

/** `FinancialEntryRead`: valores, rótulos e classificação vindos do backend. */
export interface FinancialEntry {
    id: string
    companyId: string
    values: FinancialEntryValues
    series?: {
        id: string
        mode: "MONTHLY" | "INSTALLMENTS"
        number: number
        quantity: number
    } | null
    labels: {
        bankName?: string
        categoryCode?: string
        categoryName?: string
        partnerName?: string
        centerCode?: string
        centerName?: string
    }
    effectiveCostBehavior: CostBehavior
    effectiveAmount: DecimalText
    status: FinancialEntryStatus
    asOfDate: string
    createdAt: string
    updatedAt: string
    version: number
}

export type FinancialEntryPage = PageResponse<FinancialEntry>

/**
 * `FinancialEntryCommandValues` (POST/PUT). Não inclui `actualPaidAmount`,
 * ator nem Empresa: o backend recusa esses campos.
 */
export interface FinancialEntryCommandValues {
    direction: FinancialDirection
    description: string
    nominalAmount: DecimalText
    documentDate: string
    entryDate: string
    competence: string
    dueDate: string
    paymentDate: string | null
    bankAccountId: string
    categoryId: string
    partnerId: string | null
    resultCenterId: string | null
    costBehaviorOverride: CostBehavior | null
    paymentMethod: string | null
    documentNumber: string | null
    notes: string | null
}

/** Recibo imutável de POST/PUT (`FinancialEntryCommandReceipt`). */
export interface FinancialEntryCommandReceipt {
    companyId: string
    commandId: string
    entryId: string
    action: "CREATED" | "UPDATED"
    version: number
}

/** Filtros da listagem; vazios não são enviados ao servidor. */
export interface FinancialEntryFilter {
    from: string
    to: string
    direction: FinancialDirection | ""
    status: FinancialEntryStatus | ""
    bankId: string
    categoryId: string
    /** UUID do centro raiz, ou "_none" para "Sem centro de resultado". */
    center: string
    search: string
}

export const WITHOUT_CENTER = "_none"

export const emptyFinancialEntryFilter: FinancialEntryFilter = {
    from: "",
    to: "",
    direction: "",
    status: "",
    bankId: "",
    categoryId: "",
    center: "",
    search: "",
}
