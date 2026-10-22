import { apiRequest } from "../../../lib/api/api-client"

import type {
    AccountingAccountClassification,
    DreClassification,
} from "../types/classification.types"

export function getAccountClassifications(
    companyId: string,
) {
    return apiRequest<AccountingAccountClassification[]>(
        `/api/companies/${companyId}/accounting-accounts/classifications`,
    )
}

export function getDreClassifications(
    companyId: string,
) {
    return apiRequest<DreClassification[]>(
        `/api/companies/${companyId}/dre-classifications`,
    )
}

export function classifyAccount(
    companyId: string,
    accountId: string,
    classificationId: string,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
            method: "PUT",
            body: JSON.stringify({
                classificationId,
            }),
        },
    )
}

export function unclassifyAccount(
    companyId: string,
    accountId: string,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
            method: "DELETE",
        },
    )
}