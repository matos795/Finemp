import { apiRequest } from "../../../lib/api/api-client.ts"

export interface AccountingAccountClassification {
    accountId: string
    accountingSourceId: string
    accountKey: string
    description: string
    status: "PENDING" | "CLASSIFIED"
    classificationId: string | null
    dreClassification: string | null
    dreOpenClassification: string | null
    ebitdaClassification: string | null
}

export interface DreClassification {
    id: string
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
}

export interface AccountingMovement {
    id: string
    accountingDate: string
    accountDescription: string
    accountKey: string
    side: "DEBIT" | "CREDIT"
    amount: number
    history: string
    sourceEntryKey: string
}

export function listAccountingAccounts(companyId: string) {
    return apiRequest<AccountingAccountClassification[]>(
        `/api/companies/${companyId}/accounting-accounts/classifications`,
    )
}

export function listDreClassifications() {
    return apiRequest<DreClassification[]>("/api/dre-classifications")
}

export function classifyAccountingAccount(
    companyId: string,
    accountId: string,
    classificationId: string,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
            method: "PUT",
            body: JSON.stringify({ classificationId }),
        },
    )
}

export function listAccountingMovements(
    companyId: string,
    startDate: string,
    endDate: string,
) {
    const params = new URLSearchParams({
        startDate,
        endDate,
    })

    return apiRequest<AccountingMovement[]>(
        `/api/companies/${companyId}/accounting-movements?${params.toString()}`,
    )
}
