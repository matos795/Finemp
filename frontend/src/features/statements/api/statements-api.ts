import {
    apiFetch,
    apiRequest,
} from "../../../lib/api/api-client"
import type { PageRequest } from "../../../lib/api/pagination.types"
import type {
    CsvSeparator,
    StatementFileFormat,
    StatementItemFilters,
    StatementItemPage,
    StatementPreview,
    StatementPreviewSession,
} from "../types/statement.types"

export function bankAccountStatementPath(
    companyId: string,
    bankId: string,
) {
    return `/api/companies/${encodeURIComponent(companyId)}/bank-accounts/${encodeURIComponent(bankId)}`
}

/**
 * `GET …/bank-accounts/{bankId}/statement-items` (listStatementItems).
 * Filtros, ordem (data decrescente) e paginação ficam no servidor.
 */
export async function listStatementItems(
    companyId: string,
    bankId: string,
    filters: StatementItemFilters,
    pagination: PageRequest,
): Promise<StatementItemPage> {
    const params = new URLSearchParams({
        page: String(pagination.page),
        size: String(pagination.size),
    })

    if (filters.status) {
        params.set("status", filters.status)
    }

    return apiRequest<StatementItemPage>(
        `${bankAccountStatementPath(companyId, bankId)}/statement-items?${params}`,
    )
}

/**
 * `POST …/statement-previews` (previewBankStatement): análise temporária,
 * sem gravar, conciliar nem baixar. Devolve a prévia e o SHA-256 do
 * arquivo (X-Statement-SHA256), exigido na confirmação.
 */
export async function previewStatement(
    companyId: string,
    bankId: string,
    file: File,
    format: StatementFileFormat,
    separator?: CsvSeparator,
): Promise<StatementPreviewSession> {
    const params = new URLSearchParams({ format })

    if (format === "CSV" && separator) {
        params.set("separator", separator)
    }

    const response = await apiFetch(
        `${bankAccountStatementPath(companyId, bankId)}/statement-previews?${params}`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/octet-stream",
            },
            body: file,
        },
    )

    const preview = (await response.json()) as StatementPreview

    return {
        bankId,
        file,
        format,
        separator: format === "CSV" ? separator : undefined,
        sha256: response.headers.get("X-Statement-SHA256") ?? "",
        preview,
    }
}
