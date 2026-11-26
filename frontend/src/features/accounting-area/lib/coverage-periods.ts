/**
 * Cobertura acumulada de MOVEMENTS aplicada a meses e períodos (#475).
 * A fonte é sempre a cobertura do backend (a DRE devolve uma entrada por
 * fonte); nunca a última SyncRun. Ausência de linha só vira "sem movimento"
 * quando o período inteiro está coberto.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */

export interface SourceMovementCoverage {
    accountingSourceId: string
    importedFrom: string | null
    importedTo: string | null
    intervals: { importedFrom: string; importedTo: string }[]
}

export type CoverageStatus = "COVERED" | "PARTIAL" | "NOT_IMPORTED"

export const COVERAGE_STATUS_TEXT: Record<CoverageStatus, string> = {
    COVERED: "Dados importados para todo o período",
    PARTIAL: "Cobertura parcial — existem lacunas no histórico importado",
    NOT_IMPORTED: "Histórico ainda não importado para este período",
}

export const DRE_NOT_IMPORTED_TEXT = "Histórico contábil ainda não importado para este período."

function shiftDay(iso: string, days: number): string {
    const date = new Date(`${iso}T00:00:00Z`)
    date.setUTCDate(date.getUTCDate() + days)

    return date.toISOString().slice(0, 10)
}

function lastDayOfMonth(month: string): string {
    const [year, monthNumber] = month.split("-").map((part) => Number.parseInt(part, 10))
    const day = new Date(Date.UTC(year, monthNumber, 0)).getUTCDate()

    return `${month}-${String(day).padStart(2, "0")}`
}

/** Trechos de [start, end] que os intervalos não cobrem. */
function uncovered(intervals: { importedFrom: string; importedTo: string }[], start: string, end: string) {
    const gaps: { from: string; to: string }[] = []
    let cursor = start

    const sorted = [...intervals].sort((a, b) => a.importedFrom.localeCompare(b.importedFrom))

    for (const interval of sorted) {
        if (interval.importedTo < cursor) continue
        if (interval.importedFrom > end) break

        if (interval.importedFrom > cursor) {
            gaps.push({ from: cursor, to: shiftDay(interval.importedFrom, -1) })
        }

        if (interval.importedTo >= end) {
            return gaps
        }

        cursor = shiftDay(interval.importedTo, 1)
    }

    if (cursor <= end) {
        gaps.push({ from: cursor, to: end })
    }

    return gaps
}

function statusFor(
    coverage: readonly SourceMovementCoverage[] | null | undefined,
    start: string,
    end: string,
): { status: CoverageStatus; gaps: { from: string; to: string }[] } {
    const sources = coverage ?? []

    if (sources.length === 0) {
        return { status: "NOT_IMPORTED", gaps: [{ from: start, to: end }] }
    }

    const perSource = sources.map((source) => uncovered(source.intervals ?? [], start, end))
    const fullyCovered = perSource.filter((gaps) => gaps.length === 0).length
    const untouched = perSource.filter(
        (gaps) => gaps.length === 1 && gaps[0].from === start && gaps[0].to === end,
    ).length

    // Lacunas do período: união das lacunas de cada fonte, em ordem.
    const gaps = perSource
        .flat()
        .sort((a, b) => a.from.localeCompare(b.from))
        .filter((gap, index, all) => index === 0 || gap.from !== all[index - 1].from || gap.to !== all[index - 1].to)

    if (fullyCovered === sources.length) return { status: "COVERED", gaps: [] }
    if (untouched === sources.length) return { status: "NOT_IMPORTED", gaps }

    return { status: "PARTIAL", gaps }
}

/** Situação de um mês "AAAA-MM": coberto por todas as fontes, nada importado, ou parcial. */
export function monthCoverage(
    coverage: readonly SourceMovementCoverage[] | null | undefined,
    month: string,
): CoverageStatus {
    return statusFor(coverage, `${month}-01`, lastDayOfMonth(month)).status
}

/** Situação de um período com as lacunas dentro dele e o texto da tela. */
export function periodCoverage(
    coverage: readonly SourceMovementCoverage[] | null | undefined,
    startDate: string,
    endDate: string,
) {
    const { status, gaps } = statusFor(coverage, startDate, endDate)

    return { status, gaps, text: COVERAGE_STATUS_TEXT[status] }
}

/**
 * Valor a exibir numa célula da DRE mensal. Sem linha ou com total zero, o mês
 * só vira "0,00 / sem movimento" quando está inteiramente importado; fora disso
 * fica null ("—"), porque ausência de dado não é zero. Valor diferente de zero é
 * dado real do backend e sempre aparece.
 */
export function dreCellValue(value: string | null, status: CoverageStatus): string | null {
    const zero = value === null || /^-?0+(\.0+)?$/.test(value.trim())

    if (!zero) return value
    if (status === "COVERED") return value ?? "0.00"

    return null
}
