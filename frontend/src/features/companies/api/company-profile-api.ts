import { apiRequest } from "../../../lib/api/api-client.ts"

export interface CompanyProfile {
    id: string
    legalName: string
    cnpj: string
}

export function getCompanyProfile(companyId: string) {
    return apiRequest<CompanyProfile>(
        `/api/companies/${companyId}/profile`,
    )
}
