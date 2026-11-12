import { apiRequest } from "../../../lib/api/api-client"

export interface AuditEvent {
    id: string
    action: string
    actorUserAccountId: string | null
    subjectUserAccountId: string
    accountingFirmId: string | null
    companyId: string | null
    occurredAt: string
    resourceType: string | null
    resourceId: string | null
    commandId: string | null
    beforeState: string | null
    afterState: string | null
    reason: string | null
}

export interface AuditEventPage {
    items: AuditEvent[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export function listGlobalAuditEvents(page = 0, size = 50) {
    return apiRequest<AuditEventPage>(
        `/api/admin/audit-events?page=${page}&size=${size}`,
    )
}

export function listCompanyAuditEvents(
    companyId: string,
    page = 0,
    size = 50,
) {
    return apiRequest<AuditEventPage>(
        `/api/companies/${companyId}/audit-events?page=${page}&size=${size}`,
    )
}
