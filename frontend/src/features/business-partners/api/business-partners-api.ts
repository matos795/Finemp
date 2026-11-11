import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    BusinessPartner,
    BusinessPartnerDeletion,
    BusinessPartnerFilter,
    BusinessPartnerPage,
    BusinessPartnerProfile,
} from "../types/business-partner.types.ts"

export const BUSINESS_PARTNERS_PAGE_SIZE = 50

function partnersPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/business-partners`
}

/** Parâmetros de `listBusinessPartners`; papel, situação e busca no servidor. */
export function businessPartnerQuery(
    filter: BusinessPartnerFilter,
    page: number,
) {
    const params = new URLSearchParams({
        page: String(page),
        size: String(BUSINESS_PARTNERS_PAGE_SIZE),
    })

    if (filter.role) {
        params.set("role", filter.role)
    }

    params.set("active", filter.active)

    const search = filter.search.trim()

    if (search) {
        params.set("search", search)
    }

    return params
}

/** `GET …/business-partners` (listBusinessPartners). */
export function listBusinessPartnersPage(
    companyId: string,
    filter: BusinessPartnerFilter,
    page: number,
    signal?: AbortSignal,
): Promise<BusinessPartnerPage> {
    return apiRequest<BusinessPartnerPage>(
        `${partnersPath(companyId)}?${businessPartnerQuery(filter, page)}`,
        { signal },
    )
}

/** `POST …/business-partners` (createBusinessPartner): sem expectedVersion. */
export function createBusinessPartner(
    companyId: string,
    profile: BusinessPartnerProfile,
): Promise<BusinessPartner> {
    return apiRequest<BusinessPartner>(partnersPath(companyId), {
        method: "POST",
        body: JSON.stringify({ profile }),
    })
}

/** `PUT …/business-partners/{id}`: substitui o perfil com a versão lida. */
export function updateBusinessPartner(
    companyId: string,
    partnerId: string,
    profile: BusinessPartnerProfile,
    expectedVersion: number,
): Promise<BusinessPartner> {
    return apiRequest<BusinessPartner>(
        `${partnersPath(companyId)}/${encodeURIComponent(partnerId)}`,
        {
            method: "PUT",
            body: JSON.stringify({ profile, expectedVersion }),
        },
    )
}

/**
 * `DELETE …/business-partners/{id}?version=`. O backend remove o cadastro
 * sem uso (DELETED) ou só o inativa quando há lançamentos (DEACTIVATED).
 */
export function deleteBusinessPartner(
    companyId: string,
    partnerId: string,
    version: number,
): Promise<BusinessPartnerDeletion> {
    const params = new URLSearchParams({ version: String(version) })

    return apiRequest<BusinessPartnerDeletion>(
        `${partnersPath(companyId)}/${encodeURIComponent(partnerId)}?${params}`,
        { method: "DELETE" },
    )
}
