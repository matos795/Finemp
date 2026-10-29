import type { PageResponse } from "../../../lib/api/pagination.types"

/** `FinancialBankAccountType` no OpenAPI. */
export type BankAccountType =
    | "CURRENT"
    | "SAVINGS"
    | "CASH"
    | "INVESTMENT"
    | "DIGITAL"
    | "OTHER"

/**
 * `FinancialBankAccount` no OpenAPI.
 *
 * `openingBalance` é o saldo inicial cadastrado, em string decimal
 * exata de duas casas. Não é saldo atual nem saldo calculado.
 */
export interface BankAccount {
    id: string
    companyId: string
    name: string
    type: BankAccountType
    openingBalance: string
    openingDate: string
    active: boolean
    version: number
}

export type BankAccountPage = PageResponse<BankAccount>
