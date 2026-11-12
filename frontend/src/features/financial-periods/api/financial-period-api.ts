import { apiRequest } from "../../../lib/api/api-client"

export interface FinancialPeriodState {
    id: string | null
    companyId: string
    period: string
    closed: boolean
    version: number | null
    updatedBy: string | null
    updatedAt: string | null
}

export interface FinancialPeriodHistory {
    id: string
    action: string
    actorId: string
    occurredAt: string
    beforeState: unknown
    afterState: unknown
    reason: string | null
    commandId: string
}

export interface PagedResponse<T> {
    items: T[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export interface FinancialPeriodReceipt {
    companyId: string
    commandId: string
    periodId: string
    period: string
    closed: boolean
    version: number
    auditEventId: string
}

export function listFinancialPeriods(
    companyId: string,
    page = 0,
    size = 20,
) {
    return apiRequest<PagedResponse<FinancialPeriodState>>(
        `/api/companies/${companyId}/financial-periods?page=${page}&size=${size}`,
    )
}

export function listFinancialPeriodHistory(
    companyId: string,
    period: string,
    page = 0,
    size = 20,
) {
    return apiRequest<PagedResponse<FinancialPeriodHistory>>(
        `/api/companies/${companyId}/financial-periods/${period}/history?page=${page}&size=${size}`,
    )
}

export function closeFinancialPeriod(
    companyId: string,
    period: string,
    expectedVersion: number | null,
    commandId = crypto.randomUUID(),
) {
    return apiRequest<FinancialPeriodReceipt>(
        `/api/companies/${companyId}/financial-periods/${period}/closures`,
        {
            method: "POST",
            headers: {
                "Idempotency-Key": commandId,
            },
            body: JSON.stringify({ expectedVersion }),
        },
    )
}

export function reopenFinancialPeriod(
    companyId: string,
    period: string,
    expectedVersion: number,
    reason: string,
    commandId = crypto.randomUUID(),
) {
    return apiRequest<FinancialPeriodReceipt>(
        `/api/companies/${companyId}/financial-periods/${period}/reopenings`,
        {
            method: "POST",
            headers: {
                "Idempotency-Key": commandId,
            },
            body: JSON.stringify({
                expectedVersion,
                reason: reason.trim(),
            }),
        },
    )
}
