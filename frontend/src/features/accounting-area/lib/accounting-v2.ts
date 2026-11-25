/**
 * Regras de apresentação das leituras contábeis v2 (#474/#509). Nada aqui
 * recalcula regra contábil: só traduz o que o backend devolve para a tela.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */

export type ChartAvailability = "FULL_CHART" | "IMPORTED_ACCOUNTS_ONLY"

export type AccountingDataset =
    | "CHART"
    | "MOVEMENTS"
    | "DAILY_TOTALS"
    | "MONTHLY_TOTALS"
    | "ORIGIN_CATALOG"
    | "PARTICIPANTS"
    | "COST_ALLOCATIONS"

/** GET /accounting-dataset-coverage. */
export interface DatasetCoverage {
    accountingSourceId?: string
    dataset?: AccountingDataset
    kind: "INTERVALS" | "SNAPSHOT"
    establishmentCode?: string | null
    available: boolean
    importedFrom?: string | null
    importedTo?: string | null
    intervals: { importedFrom: string; importedTo: string }[]
    publishedAt?: string | null
}

export interface MovementSourceMetadata {
    sourceRecordedAt: string | null
    historyCode: string | null
    originCode: string | null
    entryTypeCode: string | null
    dataOriginCode: string | null
    batchCode: string | null
    documentNumber: string | null
    operationGroupKey: string | null
    transactionKey: string | null
    originEstablishmentCode: string | null
}

export interface MovementMetadataCarrier {
    sourceMetadataStatus?: "SYNCHRONIZED" | "NOT_SYNCHRONIZED"
    sourceMetadata?: MovementSourceMetadata | null
}

/** Universo de contas da tela: só FULL_CHART é plano completo. */
export function chartUniverse(availability: ChartAvailability) {
    if (availability === "FULL_CHART") {
        return {
            complete: true,
            title: "Plano de Contas",
            note: "Plano completo publicado pela origem contábil (dataset CHART).",
        }
    }

    return {
        complete: false,
        title: "Contas importadas/observadas",
        note:
            "O plano de contas completo ainda não foi publicado para esta fonte. A lista mostra só as contas " +
            "que vieram nos movimentos importados: não é o plano completo.",
    }
}

export const NOT_SYNCHRONIZED_METADATA_MESSAGE = "Metadados detalhados ainda não importados para este lançamento."

/** Rótulos técnicos dos campos crus da origem; nenhum código é interpretado. */
const METADATA_FIELDS: { key: keyof MovementSourceMetadata; label: string }[] = [
    { key: "sourceRecordedAt", label: "Registrado na origem em" },
    { key: "historyCode", label: "Código de histórico" },
    { key: "originCode", label: "Código de origem" },
    { key: "entryTypeCode", label: "Código do tipo de lançamento" },
    { key: "dataOriginCode", label: "Código de origem do dado" },
    { key: "batchCode", label: "Lote" },
    { key: "documentNumber", label: "Documento" },
    { key: "operationGroupKey", label: "Grupo da operação" },
    { key: "transactionKey", label: "Chave da transação" },
    { key: "originEstablishmentCode", label: "Código do estabelecimento de origem" },
]

export type MovementMetadataView =
    | { synchronized: false; message: string }
    | {
          synchronized: true
          fields: { key: keyof MovementSourceMetadata; label: string; value: string | null; empty: boolean }[]
      }

/**
 * SYNCHRONIZED: todos os campos, e null = "vazio na origem". Qualquer outro
 * caso (inclusive resposta sem os campos novos) é "ainda não importado".
 */
export function movementMetadataView(item: MovementMetadataCarrier): MovementMetadataView {
    if (item.sourceMetadataStatus !== "SYNCHRONIZED" || !item.sourceMetadata) {
        return { synchronized: false, message: NOT_SYNCHRONIZED_METADATA_MESSAGE }
    }

    const metadata = item.sourceMetadata

    return {
        synchronized: true,
        fields: METADATA_FIELDS.map(({ key, label }) => ({
            key,
            label,
            value: metadata[key] ?? null,
            empty: metadata[key] === null || metadata[key] === undefined,
        })),
    }
}

function shiftDay(iso: string, days: number): string {
    const date = new Date(`${iso}T00:00:00Z`)
    date.setUTCDate(date.getUTCDate() + days)

    return date.toISOString().slice(0, 10)
}

export type CoverageView =
    | { state: "none"; intervals: []; gaps: []; publishedAt: null }
    | { state: "snapshot"; intervals: []; gaps: []; publishedAt: string }
    | {
          state: "intervals"
          intervals: { from: string; to: string }[]
          gaps: { from: string; to: string }[]
          publishedAt: null
      }

/** Intervalos importados como vieram do backend, mais as lacunas entre eles. */
export function describeCoverage(coverage: DatasetCoverage): CoverageView {
    if (coverage.kind === "SNAPSHOT") {
        return coverage.publishedAt
            ? { state: "snapshot", intervals: [], gaps: [], publishedAt: coverage.publishedAt }
            : { state: "none", intervals: [], gaps: [], publishedAt: null }
    }

    const intervals = (coverage.intervals ?? []).map((interval) => ({
        from: interval.importedFrom,
        to: interval.importedTo,
    }))

    if (intervals.length === 0) {
        return { state: "none", intervals: [], gaps: [], publishedAt: null }
    }

    const gaps: { from: string; to: string }[] = []

    for (let index = 1; index < intervals.length; index++) {
        const from = shiftDay(intervals[index - 1].to, 1)
        const to = shiftDay(intervals[index].from, -1)

        if (from <= to) {
            gaps.push({ from, to })
        }
    }

    return { state: "intervals", intervals, gaps, publishedAt: null }
}

function lastDayOfMonth(month: string): string {
    const [year, monthNumber] = month.split("-").map((part) => Number.parseInt(part, 10))
    const day = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()

    return `${month}-${String(day).padStart(2, "0")}`
}

/** Período dos totais em meses inteiros (o contrato MONTHLY exige; o diário usa o mesmo recorte). */
export function totalsPeriod(fromMonth: string, toMonth: string) {
    return {
        startDate: `${fromMonth}-01`,
        endDate: lastDayOfMonth(toMonth),
        valid: fromMonth <= toMonth,
    }
}

/** Permissões que o backend exige em cada leitura v2 (só UX: o servidor decide). */
export function accountingV2Access(permissions: readonly string[]) {
    const chart =
        permissions.includes("ACCOUNT_CLASSIFICATION_VIEW") || permissions.includes("ACCOUNT_CLASSIFICATION_MANAGE")
    const ledger = permissions.includes("ACCOUNTING_MOVEMENT_VIEW")

    return { chart, ledger, coverage: chart || ledger }
}

/**
 * Fontes contábeis visíveis à sessão, sem contrato novo: a lista de fontes
 * (exige QUESTOR_SYNC) e as fontes das contas importadas que a sessão já lê.
 */
export function knownSourceIds(
    syncStatus: { items: { accountingSourceId: string }[] } | null | undefined,
    importedAccounts: { items: { accountingSourceId: string }[] } | null | undefined,
): string[] {
    const ids: string[] = []

    for (const item of [...(syncStatus?.items ?? []), ...(importedAccounts?.items ?? [])]) {
        if (!ids.includes(item.accountingSourceId)) {
            ids.push(item.accountingSourceId)
        }
    }

    return ids
}

/** "2026-03-31T12:00:00Z" → "31/03/2026 09:00" (horário de Brasília). */
export function formatInstant(iso: string): string {
    const date = new Date(iso)

    if (Number.isNaN(date.getTime())) {
        return iso
    }

    return date.toLocaleString("pt-BR", {
        timeZone: "America/Sao_Paulo",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    })
}

/** "2026-03-31" → "31/03/2026"; "2026-03" → "03/2026". */
export function formatDay(iso: string): string {
    const [year, month, day] = iso.split("-")

    return day ? `${day}/${month}/${year}` : `${month}/${year}`
}
