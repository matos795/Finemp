import { apiRequest } from "../../../lib/api/api-client.ts"
import {
    FILE_IMPORT_FORMAT,
    FILE_IMPORT_LAYOUT_PROFILE,
    type FileImportHistoryItem,
    type FileImportReceipt,
    type GeneralLedgerPreview,
} from "../lib/file-import.ts"

const sourcePath = (accountingSourceId: string) =>
    `/api/accounting-sources/${encodeURIComponent(accountingSourceId)}/file-imports`

function ledgerForm(file: File) {
    const form = new FormData()
    form.set("layoutProfile", FILE_IMPORT_LAYOUT_PROFILE)
    form.set("format", FILE_IMPORT_FORMAT)
    form.set("file", file)

    return form
}

/** POST …/file-imports/general-ledger/preview (F2): análise sem gravar nada. */
export function previewGeneralLedger(accountingSourceId: string, file: File) {
    return apiRequest<GeneralLedgerPreview>(`${sourcePath(accountingSourceId)}/general-ledger/preview`, {
        method: "POST",
        body: ledgerForm(file),
    })
}

/**
 * POST …/file-imports/general-ledger/confirm (F3): reapresenta o MESMO arquivo
 * com o hash do preview e uma chave de idempotência desta confirmação.
 */
export function confirmGeneralLedger(
    accountingSourceId: string,
    request: { file: File; expectedFileSha256: string; idempotencyKey: string },
) {
    const form = ledgerForm(request.file)
    form.set("expectedFileSha256", request.expectedFileSha256)
    form.set("idempotencyKey", request.idempotencyKey)

    return apiRequest<FileImportReceipt>(`${sourcePath(accountingSourceId)}/general-ledger/confirm`, {
        method: "POST",
        body: form,
    })
}

export interface PreparedFileImportSource {
    id: string
    type: string
    connectionMode: string
    active: boolean
}

/**
 * POST /api/companies/{companyId}/accounting-file-import/source (#519): prepara
 * (ou reaproveita) a fonte FILE_IMPORT ativa. Exige ACCOUNTING_IMPORT_MANAGE,
 * nunca QUESTOR_SYNC; idempotente.
 */
export function prepareFileImportSource(companyId: string) {
    return apiRequest<PreparedFileImportSource>(
        `/api/companies/${encodeURIComponent(companyId)}/accounting-file-import/source`,
        { method: "POST" },
    )
}

/** GET …/file-imports/history (#519): receipts sanitizados, mais recentes primeiro. */
export function getFileImportHistory(accountingSourceId: string) {
    return apiRequest<FileImportHistoryItem[]>(`${sourcePath(accountingSourceId)}/history`)
}
