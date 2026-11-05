import {
    apiFetch,
    apiRequest,
} from "../../../lib/api/api-client"
import type { PageRequest } from "../../../lib/api/pagination.types"
import type {
    CsvSeparator,
    StatementCandidatePage,
    StatementFileFormat,
    StatementItemFilters,
    StatementItemPage,
    StatementImportReceipt,
    StatementPreview,
    StatementPreviewSession,
} from "../types/statement.types"
import { readFileAsBase64 } from "../lib/file-base64"

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
        companyId,
        bankId,
        file,
        format,
        separator: format === "CSV" ? separator : undefined,
        sha256: response.headers.get("X-Statement-SHA256") ?? "",
        preview,
    }
}

export interface StatementImportRequest {
    session: StatementPreviewSession
    selectedRows: number[]
    idempotencyKey: string
}

/**
 * `POST …/statement-imports` (confirmBankStatementImport). O servidor
 * confere o SHA, reprocessa o arquivo e grava só as linhas escolhidas;
 * não baixa nem concilia (D2/D3). As três confirmações são do usuário.
 */
export async function confirmStatementImport(
    companyId: string,
    { session, selectedRows, idempotencyKey }: StatementImportRequest,
): Promise<StatementImportReceipt> {
    const fileBase64 = await readFileAsBase64(session.file)

    return apiRequest<StatementImportReceipt>(
        `${bankAccountStatementPath(companyId, session.bankId)}/statement-imports`,
        {
            method: "POST",
            headers: {
                "Idempotency-Key": idempotencyKey,
            },
            body: JSON.stringify({
                format: session.format,
                ...(session.format === "CSV" ? { separator: session.separator } : {}),
                fileBase64,
                fileSha256: session.sha256,
                selectedRows,
                accountConfirmed: true,
                currencyConfirmed: true,
                warningsConfirmed: true,
            }),
        },
    )
}

/**
 * `GET /api/companies/{companyId}/statement-items/{statementId}/candidates`.
 * Só consulta: a regra (sinal, diferença, prazos e ordem) é do servidor;
 * nada é conciliado nem baixado (D3).
 */
export async function listStatementCandidates(
    companyId: string,
    statementId: string,
    pagination: PageRequest,
): Promise<StatementCandidatePage> {
    const params = new URLSearchParams({
        page: String(pagination.page),
        size: String(pagination.size),
    })

    return apiRequest<StatementCandidatePage>(
        `/api/companies/${encodeURIComponent(companyId)}/statement-items/${encodeURIComponent(statementId)}/candidates?${params}`,
    )
}
