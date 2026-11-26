import { useQueries } from "@tanstack/react-query"
import { apiErrorCode, apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getAccountingChart, getAccountingDre, type AccountingDre } from "../api/accounting-data-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { lastDayOf, monthLabel } from "../lib/accounting-period"
import { accountingV2Access, formatDay, formatInstant } from "../lib/accounting-v2"
import {
    DRE_NOT_IMPORTED_TEXT,
    dreCellValue,
    monthCoverage,
    periodCoverage,
    type CoverageStatus,
    type SourceMovementCoverage,
} from "../lib/coverage-periods"
import { buildDreMatrix, trailingMonths } from "../lib/dre-matrix"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { accountingQueryKey } from "../lib/query-keys"
import { InfoButton } from "../components/accounting-info"
import { ScreenHeader, SoonPanel, type AccountingScreenPageProps } from "./accounting-screen-page"

const MONTHS = 7

type CellState = "ok" | "failed"

const MONTH_TAG: Record<CoverageStatus, { className: string; label: string } | null> = {
    COVERED: null,
    PARTIAL: { className: "tag pend", label: "parcial" },
    NOT_IMPORTED: { className: "tag est", label: "não importado" },
}

function Cell({ value, state, coverage }: { value: string | null; state: CellState; coverage: CoverageStatus | null }) {
    if (state === "failed") {
        return (
            <td className="n muted" title="A DRE deste mês não foi calculada">
                —
            </td>
        )
    }

    const shown = dreCellValue(value, coverage)

    if (shown === null) {
        return (
            <td
                className="n muted"
                title={
                    coverage === null
                        ? "Cobertura importada indisponível"
                        : coverage === "NOT_IMPORTED"
                          ? DRE_NOT_IMPORTED_TEXT
                          : "Cobertura parcial: o mês tem lacunas no histórico importado"
                }
            >
                —
            </td>
        )
    }

    if (value === null && coverage === "COVERED") {
        // Sem linha num mês inteiramente importado: sem movimento.
        return (
            <td className="n muted" title="Sem movimento no período">
                {formatExactDecimal(shown)}
            </td>
        )
    }

    return <td className={isNegativeDecimal(shown) ? "n neg" : "n"}>{formatExactDecimal(shown)}</td>
}

function monthError(error: unknown) {
    if (apiErrorStatus(error) === 422 && apiErrorCode(error) === "DRE_UNSUPPORTED_NEGATIVE_AMOUNT") {
        return "a base contém movimento com valor negativo; a regra da DRE para esse caso ainda está em decisão (#449)."
    }

    return "não foi possível calcular."
}

/** Cobertura acumulada de MOVEMENTS devolvida pela própria DRE (uma entrada por fonte). */
function coverageOf(data: (AccountingDre | null)[]): SourceMovementCoverage[] | null {
    return data.find((month) => month !== null)?.coverage ?? null
}

function useCfpreDre(competence: string) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const months = trailingMonths(competence, MONTHS)
    const companyId = company?.companyId ?? null
    const canRead = companyGrant?.permissions.includes("DRE_VIEW") ?? false

    const results = useQueries({
        queries: months.map((month) => ({
            queryKey: accountingQueryKey(userId, "dre", companyId, month),
            queryFn: () => getAccountingDre(companyId as string, `${month}-01`, lastDayOf(month)),
            enabled: companyId !== null && canRead,
            retry: false,
        })),
    })

    const data: (AccountingDre | null)[] = results.map((result) => result.data ?? null)

    return {
        company,
        companyId,
        canRead,
        months,
        results,
        data,
        coverage: coverageOf(data),
        // Só há cobertura lida quando ao menos uma DRE do período respondeu.
        coverageKnown: data.some((month) => month !== null),
        startDate: `${months[0]}-01`,
        endDate: lastDayOf(months[MONTHS - 1]),
        loading: results.some((result) => result.isLoading),
        forbidden: results.some((result) => apiErrorStatus(result.error) === 403),
    }
}

type CfpreDre = ReturnType<typeof useCfpreDre>

function ready(dre: CfpreDre) {
    return dre.company !== null && dre.canRead && !dre.loading && !dre.forbidden
}

/** Período analisado, cobertura com lacunas, contas sem classificação e situação do plano de contas. */
function CoveragePanel({ dre, competence }: { dre: CfpreDre; competence: string }) {
    const { companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const access = accountingV2Access(companyGrant?.permissions ?? [])
    const sourceIds = (dre.coverage ?? []).map((source) => source.accountingSourceId)
    const charts = useQueries({
        queries: sourceIds.map((sourceId) => ({
            queryKey: accountingQueryKey(userId, "chart", dre.companyId, sourceId, "availability"),
            queryFn: () =>
                getAccountingChart(dre.companyId as string, { accountingSourceId: sourceId, search: "", page: 0, size: 1 }),
            enabled: dre.companyId !== null && access.chart,
            retry: false,
        })),
    })

    if (!ready(dre)) {
        return null
    }

    if (!dre.coverageKnown) {
        return (
            <div className="panel">
                <h3>Cobertura da base no período</h3>
                <div className="empty">
                    Não foi possível consultar a cobertura importada: a DRE do período não pôde ser calculada (veja o
                    motivo abaixo). Nada é concluído sobre o histórico importado.
                </div>
            </div>
        )
    }

    const period = periodCoverage(dre.coverage, dre.startDate, dre.endDate)
    const current = dre.data[MONTHS - 1]
    const published = charts
        .map((chart) => chart.data?.coverage.publishedAt)
        .filter((value): value is string => typeof value === "string")

    let chartText

    if (!access.chart) {
        chartText = <span className="muted">sem acesso ao plano de contas desta empresa.</span>
    } else if (sourceIds.length === 0) {
        chartText = <span className="muted">nenhuma fonte contábil com histórico importado.</span>
    } else if (charts.some((chart) => chart.isLoading)) {
        chartText = <span className="muted">carregando…</span>
    } else if (charts.some((chart) => chart.isError)) {
        chartText = <span className="muted">não foi possível consultar o plano de contas.</span>
    } else if (charts.every((chart) => chart.data?.availability === "FULL_CHART")) {
        chartText = (
            <>
                <span className="tag ok">Plano completo</span> (FULL_CHART) publicado em{" "}
                <b className="mono">{published.map(formatInstant).join(" · ")}</b>.
            </>
        )
    } else {
        chartText = (
            <>
                <span className="tag pend">Contas observadas</span> o plano completo ainda não foi publicado; a
                classificação considera somente contas observadas nos dados importados.
            </>
        )
    }

    return (
        <div className="panel">
            <h3>
                Cobertura da base no período
                <InfoButton title="Cobertura da base no período">
                    <p className="info-p">
                        O que já foi importado ao menos uma vez da origem contábil (movimentos), por fonte, segundo a
                        cobertura acumulada do FINEMP. Não é uma data-limite da última sincronização.
                    </p>
                </InfoButton>
            </h3>
            <div className="ctb-cov" role="status" aria-label="Cobertura do período analisado">
                <span className="ctb-cov-l">
                    Período analisado {formatDay(dre.startDate)} a {formatDay(dre.endDate)}
                </span>
                <span className="ctb-cov-v">
                    <span className={period.status === "COVERED" ? "tag ok" : "tag pend"}>{period.text}</span>
                    {period.gaps.length > 0 && (
                        <>
                            {" "}
                            Não importado:{" "}
                            {period.gaps.map((gap, index) => (
                                <span key={`${gap.from}-${gap.to}`} className="mono">
                                    {index > 0 && " · "}
                                    {formatDay(gap.from)} a {formatDay(gap.to)}
                                </span>
                            ))}
                        </>
                    )}
                </span>
            </div>
            <div className="ctb-cov" role="status" aria-label="Plano de contas">
                <span className="ctb-cov-l">Plano de contas</span>
                <span className="ctb-cov-v">{chartText}</span>
            </div>
            <div className="ctb-cov" role="status" aria-label="Contas sem classificação">
                <span className="ctb-cov-l">Contas sem classificação em {monthLabel(competence)}</span>
                <span className="ctb-cov-v">
                    {current ? (
                        current.unclassifiedAccountCount > 0 ? (
                            <>
                                <span className="tag pend">{current.unclassifiedAccountCount}</span> com movimento ficam
                                fora da DRE: classifique no De-Para do Plano de Contas.
                            </>
                        ) : (
                            <span className="tag ok">nenhuma</span>
                        )
                    ) : (
                        <span className="muted">a DRE da competência não foi calculada.</span>
                    )}
                </span>
            </div>
        </div>
    )
}

/** "DRE de pré-fechamento" com os totais do backend (#446), mês a mês, consciente da cobertura. */
function DrePanel({ dre }: { dre: CfpreDre }) {
    const { months, results, data } = dre
    let body

    if (!dre.company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!dre.canRead) {
        body = <div className="empty">Sem acesso à DRE desta empresa. Escolher a empresa não libera os dados dela.</div>
    } else if (dre.loading) {
        body = <div className="empty">Carregando…</div>
    } else if (dre.forbidden) {
        body = <div className="empty">Sem acesso à DRE desta empresa.</div>
    } else {
        // Falha de cálculo nunca vira zero: a coluna fica "—" e o motivo aparece no aviso.
        const states: CellState[] = months.map((_, index) => (results[index].isError ? "failed" : "ok"))
        const coverage = months.map((month) => (dre.coverageKnown ? monthCoverage(dre.coverage, month) : null))
        const rows = buildDreMatrix(data)
        const errors = results
            .map((result, index) => (result.isError ? `${monthLabel(months[index])}: ${monthError(result.error)}` : null))
            .filter((message): message is string => message !== null)
        const partial = months.filter((_, index) => coverage[index] === "PARTIAL").map(monthLabel)
        const notImported = months.filter((_, index) => coverage[index] === "NOT_IMPORTED").map(monthLabel)
        const nothingImported = dre.coverageKnown && notImported.length === months.length

        body = (
            <>
                {errors.length > 0 && (
                    <div className="warnbar" role="alert">
                        {errors.map((message) => (
                            <div key={message}>{message}</div>
                        ))}
                    </div>
                )}
                {partial.length > 0 && (
                    <div className="warnbar" role="status">
                        Cobertura parcial — existem lacunas no histórico importado em {partial.join(", ")}: os totais
                        desses meses podem estar incompletos.
                    </div>
                )}
                {notImported.length > 0 && !nothingImported && (
                    <div className="warnbar" role="status">
                        Histórico contábil ainda não importado para {notImported.join(", ")}: esses meses aparecem
                        como "—", não como zero.
                    </div>
                )}
                <div className="tbl-wrap" tabIndex={0} aria-label="DRE pré-fechamento">
                    <table className="dre gc">
                        <thead>
                            <tr>
                                <th>Linha da DRE</th>
                                {months.map((month, index) => {
                                    const status = coverage[index]
                                    const tag = status === null ? null : MONTH_TAG[status]

                                    return (
                                        <th key={month} className="r">
                                            {index === MONTHS - 1 ? `${monthLabel(month)} (base)` : monthLabel(month)}
                                            {tag && (
                                                <>
                                                    <br />
                                                    <span className={tag.className}>{tag.label}</span>
                                                </>
                                            )}
                                        </th>
                                    )
                                })}
                            </tr>
                        </thead>
                        <tbody>
                            {rows.map((row) => (
                                <tr
                                    key={row.key}
                                    className={row.kind === "group" ? "sub" : row.kind === "open" ? "ab" : "ab ab2"}
                                >
                                    <td title={row.label}>{row.label}</td>
                                    {row.values.map((value, index) => (
                                        <Cell
                                            key={months[index]}
                                            value={value}
                                            state={states[index]}
                                            coverage={coverage[index]}
                                        />
                                    ))}
                                </tr>
                            ))}
                            {rows.length === 0 && (
                                <tr>
                                    <td colSpan={MONTHS + 1} className="empty">
                                        {!dre.coverageKnown
                                            ? "A DRE do período não pôde ser calculada."
                                            : nothingImported
                                              ? DRE_NOT_IMPORTED_TEXT
                                              : "Nenhuma conta classificada com movimento no período importado."}
                                    </td>
                                </tr>
                            )}
                            {rows.length > 0 && (
                                <tr className="rec">
                                    <td>Soma das linhas classificadas</td>
                                    {data.map((month, index) => (
                                        <Cell
                                            key={months[index]}
                                            value={month ? month.classifiedTotal : null}
                                            state={states[index]}
                                            coverage={coverage[index]}
                                        />
                                    ))}
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </>
        )
    }

    return (
        <div className="panel">
            <h3>
                DRE de pré-fechamento
                <InfoButton title="DRE de pré-fechamento">
                    <p className="info-p">
                        Totais calculados pelo FINEMP sobre a base contábil importada, mês a mês, por linha da DRE
                        das contas classificadas no De-Para. Nenhum valor é recalculado na tela.
                    </p>
                    <p className="info-p">
                        Mês inteiramente importado e sem linha aparece como 0,00 (sem movimento). Mês não importado ou
                        com lacunas aparece como "—": ausência de dado ali não é zero.
                    </p>
                    <p className="info-p">
                        Média dos 6 meses, variação, alertas de linearidade, flags de pré-fechamento e lançamentos
                        preparados ainda não existem no FINEMP: Em breve.
                    </p>
                </InfoButton>
            </h3>
            {body}
        </div>
    )
}

/**
 * Pré-fechamento (`cfpre`): bloco de cabeçalho com a competência, cobertura
 * acumulada do período, a DRE com os totais do backend e, Em breve, alertas e
 * balancete (sem backend).
 */
export function CfpreScreen(props: AccountingScreenPageProps) {
    const { screen, competence } = props
    const dre = useCfpreDre(competence)
    // A barra mostra a cobertura acumulada do período, não a última sincronização.
    const baseText = ready(dre) && dre.coverageKnown
        ? `Cobertura importada: ${periodCoverage(dre.coverage, dre.startDate, dre.endDate).text.toLowerCase()}`
        : props.baseText

    return (
        <section id={`p-${screen.id}`} className="grid" style={{ gap: 18 }} data-handoff={screen.handoffSection}>
            <ScreenHeader {...props} baseText={baseText} />
            <CoveragePanel dre={dre} competence={competence} />
            <SoonPanel title="Alertas e recomendações" />
            <DrePanel dre={dre} />
            <SoonPanel title="Balancete de pré-fechamento" />
        </section>
    )
}
