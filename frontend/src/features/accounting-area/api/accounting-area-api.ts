import { apiRequest } from "../../../lib/api/api-client.ts"
import type { AccountingPortfolio, OrganizationAccess } from "../lib/accounting-access.ts"
import type { CompanySyncStatus } from "../lib/accounting-period.ts"

/** GET /api/auth/organization-access: contabilidades de que o usuário da sessão é membro. */
export function getOrganizationAccess(): Promise<OrganizationAccess> {
    return apiRequest<OrganizationAccess>("/api/auth/organization-access")
}

/**
 * GET /api/auth/accounting-portfolio (#435): hierarquia navegável do usuário
 * da sessão. Não concede acesso a dados: esses seguem /api/auth/me.
 */
export function getAccountingPortfolio(): Promise<AccountingPortfolio> {
    return apiRequest<AccountingPortfolio>("/api/auth/accounting-portfolio")
}

/** GET /api/companies/{companyId}/accounting-sources (#440): exige QUESTOR_SYNC na empresa. */
export function getCompanySyncStatus(companyId: string): Promise<CompanySyncStatus> {
    return apiRequest<CompanySyncStatus>(`/api/companies/${encodeURIComponent(companyId)}/accounting-sources`)
}

/**
 * POST /api/accounting-sources/{id}/sync-runs: registra a intenção de
 * sincronização INITIAL (idempotente pela chave). Exige QUESTOR_SYNC.
 */
export function requestSourceSync(sourceId: string, startDate: string, endDate: string, idempotencyKey: string) {
    return apiRequest<unknown>(`/api/accounting-sources/${encodeURIComponent(sourceId)}/sync-runs`, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({ mode: "INITIAL", startDate, endDate }),
    })
}
