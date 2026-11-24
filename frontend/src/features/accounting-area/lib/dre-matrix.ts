import type { AccountingDre } from "../api/accounting-data-api.ts"

/**
 * Monta a tabela da DRE por mês a partir das respostas do backend (#446),
 * sem calcular nada: cada célula é o total que o backend devolveu para aquele
 * mês e aquela linha (ou null quando a linha não existe no mês).
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export interface DreMatrixRow {
    kind: "group" | "open" | "line"
    key: string
    label: string
    values: (string | null)[]
}

export function buildDreMatrix(months: (AccountingDre | null)[]): DreMatrixRow[] {
    const rows = new Map<string, DreMatrixRow>()
    const order: string[] = []

    const row = (kind: DreMatrixRow["kind"], key: string, label: string) => {
        if (!rows.has(key)) {
            rows.set(key, { kind, key, label, values: months.map(() => null) })
            order.push(key)
        }

        return rows.get(key) as DreMatrixRow
    }

    // Ordem: a do mês mais recente com dados primeiro (ordem do backend), depois o que só existe antes.
    const indexes = months.map((_, index) => index).reverse()

    for (const index of indexes) {
        const month = months[index]

        for (const group of month?.groups ?? []) {
            const groupKey = group.dreClassification
            row("group", groupKey, group.dreClassification)

            for (const open of group.groups) {
                const openKey = `${groupKey}\u0000${open.dreOpenClassification}`
                row("open", openKey, open.dreOpenClassification)

                for (const line of open.lines) {
                    row("line", `${openKey}\u0000${line.ebitdaClassification}`, line.ebitdaClassification)
                }
            }
        }
    }

    for (const [index, month] of months.entries()) {
        for (const group of month?.groups ?? []) {
            rows.get(group.dreClassification)!.values[index] = group.total

            for (const open of group.groups) {
                const openKey = `${group.dreClassification}\u0000${open.dreOpenClassification}`
                rows.get(openKey)!.values[index] = open.total

                for (const line of open.lines) {
                    rows.get(`${openKey}\u0000${line.ebitdaClassification}`)!.values[index] = line.total
                }
            }
        }
    }

    // Agrupa filhos sob o pai preservando a primeira ordem vista.
    const sorted: DreMatrixRow[] = []
    const groups = order.filter((key) => rows.get(key)!.kind === "group")

    for (const groupKey of groups) {
        sorted.push(rows.get(groupKey)!)

        for (const openKey of order.filter((key) => rows.get(key)!.kind === "open" && key.startsWith(`${groupKey}\u0000`))) {
            sorted.push(rows.get(openKey)!)

            for (const lineKey of order.filter(
                (key) => rows.get(key)!.kind === "line" && key.startsWith(`${openKey}\u0000`),
            )) {
                sorted.push(rows.get(lineKey)!)
            }
        }
    }

    return sorted
}

/** Maior data sincronizada informada pela própria DRE; null quando nenhuma fonte tem base. */
export function dreCoverageUntil(dre: AccountingDre | null | undefined): string | null {
    let latest: string | null = null

    for (const item of dre?.coverage ?? []) {
        if (item.syncedEndDate && (latest === null || item.syncedEndDate > latest)) {
            latest = item.syncedEndDate
        }
    }

    return latest
}

/**
 * Competência "AAAA-MM" posterior ao fim da última sincronização concluída.
 * A cobertura da DRE é a da última execução de cada fonte, não cumulativa:
 * serve só para avisar, nunca para esconder totais calculados pelo backend.
 */
export function monthAfterLatestSync(month: string, latestEnd: string | null): boolean {
    return latestEnd !== null && `${month}-01` > latestEnd
}

/** As `count` competências terminando em `competence` ("AAAA-MM"), da mais antiga à mais recente. */
export function trailingMonths(competence: string, count: number): string[] {
    const [year, month] = competence.split("-").map(Number)

    return Array.from({ length: count }, (_, offset) => {
        const date = new Date(Date.UTC(year, month - 1 - (count - 1 - offset), 1))

        return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`
    })
}
