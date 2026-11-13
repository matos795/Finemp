import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    ServiceInvoice,
    ServiceInvoiceDraftValues,
    ServiceInvoicePage,
    ServiceInvoicePartnerOptionPage,
    ServiceInvoiceStatus,
} from "../types/service-invoice.types.ts"

const PAGE_SIZE = 50

function basePath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/service-invoices`
}

export function listServiceInvoices(
    companyId: string,
    month: string,
    status: ServiceInvoiceStatus | "",
    search: string,
    page: number,
    signal?: AbortSignal,
): Promise<ServiceInvoicePage> {
    const params = new URLSearchParams({
        month,
        page: String(page),
        size: String(PAGE_SIZE),
    })
    if (status) params.set("status", status)
    if (search.trim()) params.set("search", search.trim())

    return apiRequest<ServiceInvoicePage>(
        `${basePath(companyId)}?${params}`,
        { signal, cache: "no-store" },
    )
}

export function listServiceInvoicePartnerOptions(
    companyId: string,
    search: string,
    signal?: AbortSignal,
): Promise<ServiceInvoicePartnerOptionPage> {
    const params = new URLSearchParams({
        page: "0",
        size: String(PAGE_SIZE),
    })
    if (search.trim()) params.set("search", search.trim())

    return apiRequest<ServiceInvoicePartnerOptionPage>(
        `${basePath(companyId)}/partners?${params}`,
        { signal, cache: "no-store" },
    )
}

export function createServiceInvoice(
    companyId: string,
    values: ServiceInvoiceDraftValues,
): Promise<ServiceInvoice> {
    return apiRequest<ServiceInvoice>(basePath(companyId), {
        method: "POST",
        body: JSON.stringify({
            ...values,
            expectedVersion: null,
        }),
    })
}

export function updateServiceInvoice(
    companyId: string,
    invoice: ServiceInvoice,
    values: ServiceInvoiceDraftValues,
): Promise<ServiceInvoice> {
    return apiRequest<ServiceInvoice>(
        `${basePath(companyId)}/${encodeURIComponent(invoice.id)}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion: invoice.version,
            }),
        },
    )
}

export function markServiceInvoiceIssued(
    companyId: string,
    invoice: ServiceInvoice,
    invoiceNumber: string,
): Promise<ServiceInvoice> {
    return apiRequest<ServiceInvoice>(
        `${basePath(companyId)}/${encodeURIComponent(invoice.id)}/issuance`,
        {
            method: "POST",
            body: JSON.stringify({
                expectedVersion: invoice.version,
                invoiceNumber,
            }),
        },
    )
}

export function cancelServiceInvoice(
    companyId: string,
    invoice: ServiceInvoice,
): Promise<ServiceInvoice> {
    return apiRequest<ServiceInvoice>(
        `${basePath(companyId)}/${encodeURIComponent(invoice.id)}/cancellation`,
        {
            method: "POST",
            body: JSON.stringify({
                expectedVersion: invoice.version,
                invoiceNumber: null,
            }),
        },
    )
}
