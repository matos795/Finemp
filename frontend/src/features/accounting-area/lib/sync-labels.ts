import type { CompanySyncStatus, SyncRunSummary } from "./accounting-period.ts"

/**
 * Rótulos da situação de sincronização, no padrão de chip .stc do protótipo
 * (ok/warn/bad/info). Só fatos da SyncRun; nenhuma inferência de presença do
 * Connector.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export function runChip(run: SyncRunSummary | null): { tone: "ok" | "warn" | "bad" | "info"; label: string } {
    switch (run?.status) {
        case "SUCCEEDED":
            return { tone: "ok", label: "Concluída" }
        case "RUNNING":
            return { tone: "warn", label: "Em execução" }
        case "REQUESTED":
            return { tone: "warn", label: "Solicitada" }
        case "FAILED":
            return { tone: "bad", label: "Falhou" }
        case "CANCELLED":
            return { tone: "info", label: "Cancelada" }
        default:
            return { tone: "info", label: "Sem execução" }
    }
}

export function isRunActive(run: SyncRunSummary | null) {
    return run?.status === "REQUESTED" || run?.status === "RUNNING"
}

/** Mensagem neutra por status HTTP da solicitação (sem detalhe interno). */
export function syncRequestError(status: number | undefined) {
    switch (status) {
        case 403:
            return "Seu acesso precisa de QUESTOR_SYNC nesta empresa para sincronizar."
        case 404:
            return "A sincronização sob demanda não está habilitada neste ambiente."
        case 409:
            return "Já existe uma sincronização ativa para esta fonte, ou a fonte está indisponível."
        case 400:
            return "Confira o período informado."
        default:
            return "Não foi possível solicitar a sincronização."
    }
}

/** Identifica o conjunto de últimas execuções concluídas; muda quando uma nova sincronização termina. */
export function succeededSignature(status: CompanySyncStatus | null | undefined): string | null {
    if (!status) {
        return null
    }

    return status.items
        .map((source) => `${source.accountingSourceId}:${source.latestSucceededRun?.id ?? "-"}`)
        .sort()
        .join("|")
}
