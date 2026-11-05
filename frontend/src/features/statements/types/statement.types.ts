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

export type StatementFileFormat = "OFX" | "CSV"

export type CsvSeparator = "COMMA" | "SEMICOLON" | "TAB" | "PIPE"

/** Linha da prévia (previewBankStatement): original e valores canônicos. */
export interface StatementPreviewRow {
    number: number
    rawDate?: string
    rawAmount?: string
    rawMemo?: string
    error?: string | null
    warnings: string[]
    values?: StatementItemValues | null
}

export interface StatementPreview {
    bankCode?: string
    accountReference?: string
    currency?: string
    balance?: {
        date?: string
        amount?: string
    } | null
    warnings: string[]
    rows: StatementPreviewRow[]
}

/** Prévia + o que a confirmação precisa reapresentar ao servidor. */
export interface StatementPreviewSession {
    companyId: string
    bankId: string
    file: File
    format: StatementFileFormat
    separator?: CsvSeparator
    sha256: string
    preview: StatementPreview
}

/** Recibo da confirmação (confirmBankStatementImport). */
export interface StatementImportReceipt {
    companyId: string
    bankAccountId: string
    commandId: string
    itemIds: string[]
    auditEventId: string
}

/** Sugestão de conciliação (listStatementReconciliationCandidates). */
export interface StatementCandidate {
    entryId: string
    description: string
    direction: "INCOME" | "EXPENSE"
    bankAccountId?: string
    dueDate: string
    paymentDate?: string | null
    nominalAmount: string
    actualPaidAmount?: string | null
    version: number
    distanceDays: number
}

export type StatementCandidatePage = PageResponse<StatementCandidate>
