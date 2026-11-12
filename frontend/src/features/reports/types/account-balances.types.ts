import type { BankAccountType } from "../../bank-accounts/types/bank-account.types"

export interface AccountBalance {
    id: string
    name: string
    type: BankAccountType
    active: boolean
    openingBalance: string
    currentBalance: string
}

export interface AccountBalancesReport {
    accounts: AccountBalance[]
}
