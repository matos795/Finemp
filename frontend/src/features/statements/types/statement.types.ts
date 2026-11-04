import type { PageResponse } from "../../../lib/api/pagination.types"

/** Situação do movimento de extrato no OpenAPI (listStatementItems). */
export type StatementItemStatus =
    | "PENDING"
    | "IGNORED"
    | "RECONCILED"

/** Valores do movimento; `signedAmount` é texto decimal exato com sinal. */
export interface StatementItemValues {
    postedDate: string
    signedAmount: string
    memo: string
    externalId: string
    documentReference: string
}

export interface StatementItem {
    id: string
    bankAccountId: string
    status: StatementItemStatus
    linkedEntryId?: string | null
    createdBy?: string
    createdAt?: string
    version: number
    values: StatementItemValues
}

export type StatementItemPage = PageResponse<StatementItem>

export interface StatementItemFilters {
    status?: StatementItemStatus
}
