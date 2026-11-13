import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export type ServiceInvoiceStatus = "DRAFT" | "ISSUED" | "CANCELED"

export interface ServiceInvoice {
    id: string
    companyId: string
    businessPartnerId: string
    businessPartnerName: string
    contractId: string | null
    competence: string
    serviceText: string
    grossAmount: string
    issRate: string
    issWithheld: boolean
    irRate: string
    pisRate: string
    cofinsRate: string
    csllRate: string
    inssRate: string
    issAmount: string
    irAmount: string
    pisAmount: string
    cofinsAmount: string
    csllAmount: string
    inssAmount: string
    totalWithheld: string
    netAmount: string
    status: ServiceInvoiceStatus
    invoiceNumber: string | null
    version: number
    createdAt: string
    updatedAt: string
}

export type ServiceInvoicePage = PageResponse<ServiceInvoice>

export interface ServiceInvoicePartnerOption {
    id: string
    name: string
}

export type ServiceInvoicePartnerOptionPage =
    PageResponse<ServiceInvoicePartnerOption>

export interface ServiceInvoiceDraftValues {
    businessPartnerId: string
    contractId: string | null
    competence: string
    serviceText: string
    grossAmount: string
    issRate: string
    issWithheld: boolean
    irRate: string
    pisRate: string
    cofinsRate: string
    csllRate: string
    inssRate: string
}
