/**
 * Importação do Razão por arquivo (FILE_IMPORT, F4 / #483). Regras do fluxo
 * sem dependências de runtime: são importadas pelos testes node.
 *
 * O frontend não calcula nem confia em movimento nenhum: envia o arquivo, o
 * backend pré-visualiza e, na confirmação, recebe de novo o MESMO arquivo com o
 * hash devolvido pelo preview. Hash, fingerprint e chave de idempotência nunca
 * aparecem na tela.
 */
import { ApiError } from "../../../lib/api/api-client.ts"

/** Único layout homologado na primeira fatia (F2/F3). */
export const FILE_IMPORT_LAYOUT_PROFILE = "analytical-ledger-html-v1"
export const FILE_IMPORT_FORMAT = "HTML_XLS"

export interface FileImportSourceCandidate {
    id: string
    type: string
    active: boolean
}

export interface GeneralLedgerPreviewIssue {
    code: string
    sourceRow: number | null
    message: string
}

export interface GeneralLedgerPreviewRow {
    sourceRow: number
    sourceAccountId: string
    accountDescription: string | null
    accountingDate: string
    sourceEntryKey: string
    side: "DEBIT" | "CREDIT"
    amount: string | number
    history: string | null
}

export interface GeneralLedgerPreview {
    fileSha256: string
    format: string
    layoutProfile: string
    periodStart: string | null
    periodEnd: string | null
    totalRows: number
    movementCount: number
    invalidRowCount: number
    observedAccountCount: number
    warnings: GeneralLedgerPreviewIssue[]
    errors: GeneralLedgerPreviewIssue[]
    sampleRows: GeneralLedgerPreviewRow[]
}

export interface FileImportReceipt {
    accountingSourceId: string
    coveredFrom: string
    coveredTo: string
    rowCount: number
    publishedCount: number
    skippedCount: number
    committedAt: string
}

export interface FileImportHistoryItem {
    coveredFrom: string
    coveredTo: string
    rowCount: number
    publishedCount: number
    skippedCount: number
    committedAt: string
}

/** Só fontes FILE_IMPORT ativas participam da importação por arquivo. */
export function fileImportSources<T extends FileImportSourceCandidate>(sources: readonly T[] | null | undefined): T[] {
    return (sources ?? []).filter((source) => source.type === "FILE_IMPORT" && source.active)
}

/** Uma fonte: usada automaticamente. Várias: só a escolha explícita, se ainda estiver na lista. */
export function selectFileImportSource(sources: readonly { id: string }[], chosen: string | null): string | null {
    if (sources.length === 1) return sources[0].id
    return sources.some((source) => source.id === chosen) ? chosen : null
}

// --------------------------------------------------------------- the flow

export type FileImportStatus =
    | "waiting-file"
    | "file-selected"
    | "previewing"
    | "preview-ready"
    | "confirming"
    | "done"

export interface FileImportState {
    status: FileImportStatus
    file: File | null
    /** Muda a cada arquivo escolhido: respostas de um arquivo anterior são descartadas. */
    fileToken: number
    /** Fonte em que o preview foi feito: a confirmação só vai para ela. */
    sourceId: string | null
    preview: GeneralLedgerPreview | null
    idempotencyKey: string | null
    receipt: FileImportReceipt | null
    error: string | null
}

export const initialFileImportState: FileImportState = {
    status: "waiting-file",
    file: null,
    fileToken: 0,
    sourceId: null,
    preview: null,
    idempotencyKey: null,
    receipt: null,
    error: null,
}

export type FileImportAction =
    | { type: "select-file"; file: File | null }
    | { type: "preview-started"; sourceId?: string }
    | { type: "preview-succeeded"; preview: GeneralLedgerPreview; fileToken?: number }
    | { type: "preview-failed"; message: string; fileToken?: number }
    | { type: "confirm-started"; idempotencyKey: string; sourceId?: string }
    | { type: "source-changed"; sourceId: string | null }
    | { type: "confirm-succeeded"; receipt: FileImportReceipt }
    | { type: "confirm-failed"; message: string }
    | { type: "reset" }

function previewHasNoErrors(preview: GeneralLedgerPreview | null) {
    return preview !== null && preview.errors.length === 0 && preview.periodStart !== null && preview.periodEnd !== null
}

/**
 * Confirmar só com preview válido, do arquivo atual, sem confirmação em andamento
 * e (quando informada) na mesma fonte em que o preview foi feito.
 */
export function previewIsConfirmable(state: FileImportState, currentSourceId?: string | null) {
    return (
        state.status === "preview-ready" &&
        state.file !== null &&
        previewHasNoErrors(state.preview) &&
        (currentSourceId === undefined || state.sourceId === null || state.sourceId === currentSourceId)
    )
}

export function fileImportReducer(state: FileImportState, action: FileImportAction): FileImportState {
    switch (action.type) {
        case "select-file":
            // Outro arquivo invalida o preview anterior.
            return {
                ...initialFileImportState,
                status: action.file ? "file-selected" : "waiting-file",
                file: action.file,
                fileToken: state.fileToken + 1,
            }
        case "preview-started":
            if (!state.file || state.status === "confirming" || state.status === "previewing") return state
            return {
                ...state,
                status: "previewing",
                sourceId: action.sourceId ?? state.sourceId,
                preview: null,
                error: null,
                receipt: null,
            }
        case "preview-succeeded":
            if (state.status !== "previewing") return state
            if (action.fileToken !== undefined && action.fileToken !== state.fileToken) return state
            return { ...state, status: "preview-ready", preview: action.preview, error: null }
        case "preview-failed":
            if (state.status !== "previewing") return state
            if (action.fileToken !== undefined && action.fileToken !== state.fileToken) return state
            return { ...state, status: "file-selected", preview: null, error: action.message }
        case "confirm-started":
            // Duplo clique e preview inválido não geram uma segunda confirmação.
            if (!previewIsConfirmable(state, action.sourceId)) return state
            return { ...state, status: "confirming", idempotencyKey: action.idempotencyKey, error: null }
        case "confirm-succeeded":
            if (state.status !== "confirming") return state
            return { ...state, status: "done", receipt: action.receipt, error: null }
        case "confirm-failed":
            if (state.status !== "confirming") return state
            // Volta ao preview válido: uma nova tentativa explícita usa nova chave.
            return { ...state, status: "preview-ready", idempotencyKey: null, error: action.message }
        case "source-changed":
            // A fonte efetiva mudou (escolha ou atualização da lista): o preview não vale mais.
            if (state.sourceId === null || state.sourceId === action.sourceId) return state
            if (state.status === "confirming") return state
            return { ...initialFileImportState, fileToken: state.fileToken + 1 }
        case "reset":
            return { ...initialFileImportState, fileToken: state.fileToken + 1 }
    }
}

/** Corpo da confirmação: o mesmo File do preview e o hash devolvido por ele. */
export function confirmRequest(state: FileImportState) {
    if (state.status !== "confirming" || !state.file || !state.preview || !state.idempotencyKey) {
        throw new Error("A confirmação exige um preview válido em andamento.")
    }

    return {
        accountingSourceId: state.sourceId,
        file: state.file,
        expectedFileSha256: state.preview.fileSha256,
        idempotencyKey: state.idempotencyKey,
        layoutProfile: FILE_IMPORT_LAYOUT_PROFILE,
        format: FILE_IMPORT_FORMAT,
    }
}

// --------------------------------------------------------------- messages

const ERROR_MESSAGES: Record<string, string> = {
    INVALID_GENERAL_LEDGER: "O arquivo não pôde ser lido como Razão no layout homologado (Razão Analítico em .xls).",
    LAYOUT_UNAVAILABLE: "O layout de Razão escolhido não está disponível para este formato.",
    INVALID_FILE: "O arquivo enviado está vazio, inválido ou excede o limite permitido.",
    SOURCE_NOT_AVAILABLE: "A fonte por arquivo não está disponível. Atualize a página e tente de novo.",
    IDEMPOTENCY_CONFLICT: "Esta confirmação já foi usada para outra importação. Tente confirmar de novo.",
    PERIOD_OVERLAP:
        "O período deste arquivo se sobrepõe a um período já importado nesta fonte. Nada foi alterado.",
    PUBLISHED_DATA_CONFLICT: "Os lançamentos do arquivo conflitam com dados já importados nesta fonte. Nada foi alterado.",
    PREVIEW_FILE_MISMATCH:
        "O arquivo enviado na confirmação é diferente do arquivo pré-visualizado. Escolha o arquivo e pré-visualize de novo.",
}

/** Mensagem pt-BR sanitizada: nunca repassa texto técnico do servidor ou do navegador. */
export function fileImportErrorMessage(error: unknown): string {
    if (error instanceof ApiError) {
        if (error.code && ERROR_MESSAGES[error.code]) return ERROR_MESSAGES[error.code]
        if (error.status === 403) return "Sem permissão para importar dados contábeis por arquivo nesta empresa."
        if (error.status === 413) return ERROR_MESSAGES.INVALID_FILE
    }

    return "Não foi possível concluir a operação. Tente de novo em instantes."
}

/** Histórico de importações: só período, contagens e data (nada técnico). */
export function historyRows(items: readonly FileImportHistoryItem[] | null | undefined) {
    return (items ?? []).map((item) => ({
        coveredFrom: item.coveredFrom,
        coveredTo: item.coveredTo,
        rowCount: item.rowCount,
        publishedCount: item.publishedCount,
        skippedCount: item.skippedCount,
        committedAt: item.committedAt,
    }))
}
