import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    DebtAgreement,
    DebtAgreementPage,
    DebtAgreementValues,
    DebtInstallment,
    DebtOrigin,
    DebtPartnerOptionPage,
} from "../types/debt.types.ts"

export const DEBTS_PAGE_SIZE = 50

function debtsPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/debts`
}

export function listDebts(
    companyId: string,
    origin: DebtOrigin,
    search: string,
    page: number,
    signal?: AbortSignal,
): Promise<DebtAgreementPage> {
    const params = new URLSearchParams({
        origin,
        page: String(page),
        size: String(DEBTS_PAGE_SIZE),
    })

    const text = search.trim()
    if (text) params.set("search", text)

    return apiRequest<DebtAgreementPage>(
        `${debtsPath(companyId)}?${params}`,
        { signal, cache: "no-store" },
    )
}

export function listDebtPartnerOptions(
    companyId: string,
    search: string,
    signal?: AbortSignal,
): Promise<DebtPartnerOptionPage> {
    const params = new URLSearchParams({
        page: "0",
        size: String(DEBTS_PAGE_SIZE),
    })

    const text = search.trim()
    if (text) params.set("search", text)

    return apiRequest<DebtPartnerOptionPage>(
        `${debtsPath(companyId)}/partners?${params}`,
        { signal, cache: "no-store" },
    )
}

export function getDebtSchedule(
    companyId: string,
    debtId: string,
    signal?: AbortSignal,
): Promise<DebtInstallment[]> {
    return apiRequest<DebtInstallment[]>(
        `${debtsPath(companyId)}/${encodeURIComponent(debtId)}/schedule`,
        { signal, cache: "no-store" },
    )
}

export function createDebt(
    companyId: string,
    values: DebtAgreementValues,
): Promise<DebtAgreement> {
    return apiRequest<DebtAgreement>(
        debtsPath(companyId),
        {
            method: "POST",
            body: JSON.stringify({
                ...values,
                expectedVersion: null,
            }),
        },
    )
}

export function updateDebt(
    companyId: string,
    debt: DebtAgreement,
    values: DebtAgreementValues,
): Promise<DebtAgreement> {
    return apiRequest<DebtAgreement>(
        `${debtsPath(companyId)}/${encodeURIComponent(debt.id)}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion: debt.version,
            }),
        },
    )
}
