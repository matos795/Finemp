/**
 * Diagnóstico das Bases (#530), parcial: só verificações de integridade da base
 * contábil calculadas a partir de contratos oficiais (DRE, cobertura, plano,
 * contas importadas, SyncRun). Nenhum valor contábil é recalculado e nenhuma
 * gravidade é inventada: cada verificação está aprovada, requer atenção, ou não
 * pôde ser verificada (sem acesso / indisponível / carregando).
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
import { formatDay } from "./accounting-v2.ts"
import { periodCoverage, type SourceMovementCoverage } from "./coverage-periods.ts"

export type CheckStatus = "ok" | "attention" | "no-access" | "unavailable" | "loading"

export interface DiagnosticCheck {
    id: string
    label: string
    status: CheckStatus
    detail: string
    /** Tela da Área da Contabilidade onde a pendência se resolve. */
    resolveScreenId?: string
}

type Loaded<T> =
    | ({ state: "ok" } & T)
    | { state: "forbidden" }
    | { state: "error" }
    | { state: "loading" }

const NOT_VERIFIED: Record<"forbidden" | "error" | "loading", { status: CheckStatus; detail: string }> = {
    forbidden: { status: "no-access", detail: "Seu acesso não permite consultar este dado nesta empresa." },
    error: { status: "unavailable", detail: "Não foi possível consultar este dado agora." },
    loading: { status: "loading", detail: "Verificando…" },
}

const LABELS = {
    coverage: "Movimentos importados cobrem o período analisado",
    unclassified: "Contas com movimento no período classificadas na DRE",
    dreComputable: "DRE calculável no período",
    pendingAccounts: "Contas importadas classificadas",
    chart: "Plano de contas completo publicado",
    sync: "Última sincronização Questor aceita pela validação da base",
}

/** Três verificações a partir de GET /dre do período (cobertura, classificação e cálculo). */
export function dreChecks(
    input:
        | Loaded<{ dre: { unclassifiedAccountCount: number; coverage?: SourceMovementCoverage[] | null } }>
        | { state: "negative-amount" },
    startDate: string,
    endDate: string,
): DiagnosticCheck[] {
    if (input.state === "negative-amount") {
        const dependent = {
            status: "unavailable" as const,
            detail: "Depende da DRE do período, que não pôde ser calculada.",
        }

        return [
            { id: "coverage", label: LABELS.coverage, ...dependent },
            { id: "unclassified", label: LABELS.unclassified, ...dependent },
            {
                id: "dreComputable",
                label: LABELS.dreComputable,
                status: "attention",
                detail:
                    "A base contém movimento com valor negativo no período; a DRE recusa o cálculo enquanto a regra " +
                    "para esse caso está em decisão (#449).",
            },
        ]
    }

    if (input.state !== "ok") {
        const not = NOT_VERIFIED[input.state]
        return ["coverage", "unclassified", "dreComputable"].map((id) => ({
            id,
            label: LABELS[id as keyof typeof LABELS],
            ...not,
        }))
    }

    const period = periodCoverage(input.dre.coverage ?? [], startDate, endDate)
    const gaps = period.gaps.map((gap) => `${formatDay(gap.from)} a ${formatDay(gap.to)}`).join(" · ")
    const unclassified = input.dre.unclassifiedAccountCount

    return [
        {
            id: "coverage",
            label: LABELS.coverage,
            status: period.status === "COVERED" ? "ok" : "attention",
            detail:
                period.status === "COVERED"
                    ? `${period.text}.`
                    : `${period.text}. Não importado: ${gaps}.`,
            resolveScreenId: period.status === "COVERED" ? undefined : "ctbmov",
        },
        {
            id: "unclassified",
            label: LABELS.unclassified,
            status: unclassified === 0 ? "ok" : "attention",
            detail:
                unclassified === 0
                    ? "Todas as contas com movimento no período têm linha da DRE."
                    : `${unclassified} ${unclassified === 1 ? "conta" : "contas"} com movimento no período sem linha da DRE ficam fora do resultado.`,
            resolveScreenId: unclassified === 0 ? undefined : "depara",
        },
        {
            id: "dreComputable",
            label: LABELS.dreComputable,
            status: "ok",
            detail: "A DRE do período foi calculada pelo backend sem recusas.",
        },
    ]
}

/** Contas importadas pendentes (GET /accounting-accounts → summary). */
export function accountsCheck(input: Loaded<{ summary: { total: number; classified: number; pending: number } }>): DiagnosticCheck {
    if (input.state !== "ok") return { id: "pendingAccounts", label: LABELS.pendingAccounts, ...NOT_VERIFIED[input.state] }

    const { total, pending } = input.summary

    return {
        id: "pendingAccounts",
        label: LABELS.pendingAccounts,
        status: pending === 0 ? "ok" : "attention",
        detail:
            pending === 0
                ? `Todas as ${total} contas importadas têm classificação na DRE.`
                : `${pending} de ${total} contas importadas ainda sem classificação na DRE.`,
        resolveScreenId: pending === 0 ? undefined : "depara",
    }
}

/** Plano completo por fonte (GET /accounting-chart). Fonte por arquivo só traz contas observadas. */
export function chartCheck(
    input:
        | Loaded<{ sources: { type: string; availability: "FULL_CHART" | "IMPORTED_ACCOUNTS_ONLY"; publishedAt: string | null }[] }>,
): DiagnosticCheck {
    if (input.state !== "ok") return { id: "chart", label: LABELS.chart, ...NOT_VERIFIED[input.state] }

    if (input.sources.length === 0) {
        return { id: "chart", label: LABELS.chart, status: "unavailable", detail: "Nenhuma fonte contábil ativa nesta empresa." }
    }

    const questorMissing = input.sources.filter(
        (source) => source.type !== "FILE_IMPORT" && source.availability !== "FULL_CHART",
    ).length
    const onlyFiles = input.sources.every((source) => source.type === "FILE_IMPORT")

    if (questorMissing > 0) {
        return {
            id: "chart",
            label: LABELS.chart,
            status: "attention",
            detail:
                "O plano completo ainda não foi publicado: a classificação considera só as contas observadas nos " +
                "dados importados.",
            resolveScreenId: "plano",
        }
    }

    return {
        id: "chart",
        label: LABELS.chart,
        status: "ok",
        detail: onlyFiles
            ? "Fonte por arquivo: o Razão importado traz só contas observadas; o plano completo não se aplica."
            : "O plano de contas completo está publicado.",
    }
}

const FAILURE_LABELS: Record<string, string> = {
    INVALID_PAYLOAD: "dados recusados pela validação da base",
    DATA_CONFLICT: "conflito com dados já publicados",
    READ_FAILED: "falha de leitura na origem",
    IDENTITY_MISMATCH: "identidade da empresa na origem não confere",
    PUBLICATION_FAILED: "falha ao publicar os dados",
    LEASE_EXPIRED: "execução interrompida",
    RETRY_LIMIT: "limite de tentativas atingido",
    SOURCE_NOT_AVAILABLE: "fonte indisponível",
    ACCESS_REVOKED: "acesso revogado",
    UNSUPPORTED_MODE: "modo de sincronização não suportado",
    RESOURCE_LIMIT: "volume acima do limite",
}

/** Última SyncRun por fonte Questor (GET /accounting-sources, exige QUESTOR_SYNC). */
export function syncCheck(
    input: Loaded<{ items: { type: string; latestRun: { status: string; failureCode: string | null; endDate?: string } | null }[] }>,
): DiagnosticCheck {
    if (input.state !== "ok") return { id: "sync", label: LABELS.sync, ...NOT_VERIFIED[input.state] }

    const failed = input.items.filter((item) => item.latestRun?.status === "FAILED")

    if (failed.length === 0) {
        return {
            id: "sync",
            label: LABELS.sync,
            status: "ok",
            detail: "Nenhuma sincronização Questor recusada na última execução de cada fonte.",
        }
    }

    const codes = failed
        .map((item) => {
            const code = item.latestRun?.failureCode ?? "sem código"
            return `${FAILURE_LABELS[code] ?? "falha"} (código ${code})`
        })
        .join("; ")

    return {
        id: "sync",
        label: LABELS.sync,
        status: "attention",
        detail: `A última sincronização foi recusada: ${codes}. Os dados anteriores continuam publicados.`,
    }
}

/** Painéis do protótipo: Inconsistências, Verificações aprovadas e as não verificadas. */
export function groupChecks<T extends { status: CheckStatus }>(checks: readonly T[]) {
    return {
        inconsistencies: checks.filter((check) => check.status === "attention"),
        approved: checks.filter((check) => check.status === "ok"),
        notVerified: checks.filter((check) => check.status !== "attention" && check.status !== "ok"),
    }
}

/** Verificações do protótipo que dependem de balanço, saldos e encerramentos (backend ainda não publica). */
export const BALANCE_DEPENDENT_CHECKS = [
    {
        label: "Ativo = Passivo + PL em cada mês (tolerância R$ 0,01)",
        reason: "Exige saldos de balanço por conta; a base ainda não publica saldo inicial nem encerramentos.",
    },
    {
        label: "DRE = variação do resultado do exercício no balanço",
        reason: "Exige o balanço e os lançamentos de encerramento, ainda não publicados.",
    },
    {
        label: "Lançamentos manuais (pendente, sem contrapartida, mês fechado, não exportados)",
        reason: "Lançamentos preparados e contrapartida ainda não existem no FINEMP.",
    },
] as const

/**
 * Período verificado: a escolha do usuário ou, sem ela, os 12 meses que terminam
 * na competência da área (acompanha a competência quando ela muda).
 */
export function diagnosticsPeriod(chosen: { from: string; to: string } | null, competence: string) {
    if (chosen) return chosen

    const [year, month] = competence.split("-").map((part) => Number.parseInt(part, 10))
    const start = new Date(Date.UTC(year, month - 12, 1))

    return { from: `${start.getUTCFullYear()}-${String(start.getUTCMonth() + 1).padStart(2, "0")}`, to: competence }
}
