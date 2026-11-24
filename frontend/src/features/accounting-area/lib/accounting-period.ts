/**
 * Competência e "Base contábil até" da barra cfBar do protótipo.
 *
 * A base vem de GET /api/companies/{companyId}/accounting-sources (#440):
 * a maior data final entre as últimas execuções SUCCEEDED das fontes. Sem
 * execução concluída não há base: nada é inventado.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */

const MONTHS = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]

export interface SyncRunSummary {
    id: string
    status: "REQUESTED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED"
    mode: string
    startDate: string
    endDate: string
    requestedAt: string
    finishedAt: string | null
    readCount: number | null
    importedCount: number | null
    skippedCount: number | null
    failureCode: string | null
}

/** Schema CompanySyncStatusSource (OpenAPI listCompanySyncStatus). */
export interface CompanySyncSource {
    accountingSourceId: string
    type: string
    connectionMode: "DIRECT" | "CONNECTOR"
    active: boolean
    latestRun: SyncRunSummary | null
    latestSucceededRun: SyncRunSummary | null
}

export interface CompanySyncStatus {
    items: CompanySyncSource[]
}

/** "AAAA-MM-DD" ou "AAAA-MM" → "set/26" (rótulo mlab do protótipo). */
export function monthLabel(date: string): string {
    const [year, month] = date.split("-")
    const index = Number(month) - 1

    if (!year || !(index >= 0 && index < 12)) {
        return date
    }

    return `${MONTHS[index]}/${year.slice(2)}`
}

/** Última data coberta por uma sincronização concluída, entre todas as fontes; null sem base. */
export function syncedUntil(status: CompanySyncStatus | null | undefined): string | null {
    let latest: string | null = null

    for (const source of status?.items ?? []) {
        const end = source.latestSucceededRun?.endDate

        if (end && (latest === null || end > latest)) {
            latest = end
        }
    }

    return latest
}

/** "AAAA-MM" do mês seguinte (competência padrão do protótipo: mês seguinte à base). */
export function nextMonth(date: string): string {
    const [year, month] = date.split("-").map(Number)
    const nextYear = month === 12 ? year + 1 : year
    const nextMonthNumber = month === 12 ? 1 : month + 1

    return `${nextYear}-${String(nextMonthNumber).padStart(2, "0")}`
}

/** Competência padrão: mês seguinte à base; sem base, o mês corrente. */
export function defaultCompetence(baseUntil: string | null, today: Date): string {
    if (baseUntil) {
        return nextMonth(baseUntil)
    }

    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
}
