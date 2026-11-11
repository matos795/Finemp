import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export type BusinessPartnerRole = "CUSTOMER" | "SUPPLIER" | "BOTH"

export type PartnerPersonType = "INDIVIDUAL" | "LEGAL_ENTITY"

/**
 * `BusinessPartnerProfile`: cliente/fornecedor comercial da Company (não é
 * o Client SaaS da contabilidade). Documento, unicidade, UF e código
 * municipal são validados e normalizados pelo backend.
 */
export interface BusinessPartnerProfile {
    personType: PartnerPersonType
    document: string
    name: string
    tradeName: string | null
    role: BusinessPartnerRole
    active: boolean
    phone: string | null
    email: string | null
    contactName: string | null
    city: string | null
    stateCode: string | null
    postalCode: string | null
    street: string | null
    streetNumber: string | null
    complement: string | null
    district: string | null
    municipalityCode: string | null
    stateRegistration: string | null
    municipalRegistration: string | null
    notes: string | null
}

export interface BusinessPartner {
    id: string
    companyId?: string
    profile: BusinessPartnerProfile
    version: number
    createdAt?: string
    updatedAt?: string
}

export type BusinessPartnerPage = PageResponse<BusinessPartner>

/** Resposta de DELETE: o backend decide remover ou só inativar. */
export interface BusinessPartnerDeletion {
    id: string
    action: "DELETED" | "DEACTIVATED"
}

export interface BusinessPartnerFilter {
    role: BusinessPartnerRole | ""
    /** "true" (ativos, padrão do protótipo) ou "false" (inativos). */
    active: "true" | "false"
    search: string
}

export const defaultBusinessPartnerFilter: BusinessPartnerFilter = {
    role: "",
    active: "true",
    search: "",
}
