import { useState } from "react"
import { QueryError } from "../../../components/finemp/query-error"
import type { OperationalDre } from "../../../lib/api/presentation-client"
import {
    formatExactDecimalCell,
    formatExactDecimal,
    isNegativeExactDecimal,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { monthLabels } from "../../reports/lib/calendar-labels"
import { OperationalDreEffectsDialog } from "../components/operational-dre-effects-dialog"
import {
    monthOf,
    useOperationalDreYear,
} from "../hooks/use-operational-dre"
import {
    dreLineLabels,
    dreStructure,
    type OperationalDreLine,
} from "../lib/dre-structure"

const PENDING_TITLE = "Aguardando dado da API"

/** Total do período, Média mensal e AV%: ainda sem dado anual na API. */
const PENDING_COLUMNS = 3

const errorMessages = {
    400: "Confira o ano.",
    403: "Você não tem acesso à DRE operacional desta empresa.",
    404: "A DRE operacional não está disponível neste ambiente.",
}

const zeroPattern = /^-?(?:0+(?:\.0*)?|\.0+)$/

function amountClass(value: string | undefined) {
    return value !== undefined && isNegativeExactDecimal(value) ? "n neg" : "n"
}

/** Células pendentes; `medAt` marca a coluna Média mensal (td.med). */
function PendingCells({
    count,
    medAt,
}: {
    count: number
    medAt: number
}) {
    return (
        <>
            {Array.from({ length: count }, (_, index) => (
                <td
                    key={index}
                    className={index === medAt ? "n med" : "n"}
                    title={PENDING_TITLE}
                >
                    —
                </td>
            ))}
        </>
    )
}

/**
 * Relatórios › DRE (competência): DRE operacional mensal (#41), limitada
 * aos efeitos dos lançamentos financeiros. Linhas e subtotais vêm do
 * backend; os lançamentos de cada célula vêm de operational-dre/effects.
 * Não é a DRE contábil (Questor), que continua em Demais › DRE contábil
 * (PA7). Total, média, AV%, margens, cabeçalhos com soma e abertura por
 * categoria ficam pendentes, sem cálculo no frontend.
 */
export function OperationalDrePage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id
    const currentYear = Number(todayIso().slice(0, 4))

    const [year, setYear] = useState(currentYear)
    const [firstMonth, setFirstMonth] = useState(0)
    const [lastMonth, setLastMonth] = useState(11)
    const [effectsTarget, setEffectsTarget] = useState<{
        month: string
        line: OperationalDreLine
        title: string
    } | null>(null)

    const months = useOperationalDreYear(companyId, year)

    const years = Array.from({ length: 6 }, (_, index) => currentYear + 1 - index)

    const monthIndexes = Array.from(
        { length: lastMonth - firstMonth + 1 },
        (_, index) => firstMonth + index,
    )

    const failed = months.find((month) => month.isError)

    const reports: (OperationalDre | undefined)[] =
        months.map((month) => month.data)

    function lineAmount(report: OperationalDre | undefined, line: OperationalDreLine) {
        return report?.lines.find((candidate) => candidate.line === line)
    }

    const hasDepreciation = monthIndexes.some((index) => {
        const amount = lineAmount(reports[index], "DEP")?.amount

        return amount !== undefined && !zeroPattern.test(amount.trim())
    })

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="drAno">Ano</label>
                    <select
                        id="drAno"
                        value={year}
                        onChange={(event) => setYear(Number(event.target.value))}
                    >
                        {years.map((option) => (
                            <option
                                key={option}
                                value={option}
                            >
                                {option}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="drCR">Centro de resultado</label>
                    <select
                        id="drCR"
                        disabled
                        title={PENDING_TITLE}
                    >
                        <option>Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label>Visão</label>
                    <div className="tabs">
                        <button
                            type="button"
                            className="pill on"
                        >
                            Mensal
                        </button>
                        <button
                            type="button"
                            className="pill"
                            disabled
                            title={PENDING_TITLE}
                        >
                            Trimestral
                        </button>
                        <button
                            type="button"
                            className="pill"
                            disabled
                            title={PENDING_TITLE}
                        >
                            Anual
                        </button>
                    </div>
                </div>

                <div className="f">
                    <label htmlFor="perDe">Período</label>
                    <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                        <select
                            id="perDe"
                            value={firstMonth}
                            onChange={(event) => {
                                const value = Number(event.target.value)
                                setFirstMonth(value)
                                setLastMonth((previous) => Math.max(previous, value))
                            }}
                        >
                            {monthLabels.map((label, index) => (
                                <option
                                    key={label}
                                    value={index}
                                >
                                    {label}
                                </option>
                            ))}
                        </select>
                        <span className="mut">até</span>
                        <select
                            id="perAte"
                            value={lastMonth}
                            onChange={(event) => {
                                const value = Number(event.target.value)
                                setLastMonth(value)
                                setFirstMonth((previous) => Math.min(previous, value))
                            }}
                        >
                            {monthLabels.map((label, index) => (
                                <option
                                    key={label}
                                    value={index}
                                >
                                    {label}
                                </option>
                            ))}
                        </select>
                    </div>
                </div>

                <button
                    type="button"
                    className="btn sec sm"
                    disabled
                    title={PENDING_TITLE}
                >
                    Abrir tudo
                </button>
                <button
                    type="button"
                    className="btn sec sm"
                    disabled
                    title={PENDING_TITLE}
                >
                    Fechar tudo
                </button>
                <button
                    type="button"
                    className="btn sec sm"
                    disabled
                    title={PENDING_TITLE}
                >
                    Exportar (.xlsx)
                </button>
                <button
                    type="button"
                    className="btn sec sm"
                    onClick={() => window.print()}
                >
                    Imprimir / PDF
                </button>
            </div>

            <div className="alert i">
                DRE operacional parcial: a cobertura atual considera apenas
                efeitos dos lançamentos financeiros (FINANCIAL_ENTRIES).
                Outras fontes operacionais ainda não estão integradas. Não é a
                DRE contábil, que continua em Demais › DRE contábil.
            </div>

            <div className="panel">
                <h2>DRE {year} – regime de competência</h2>
                <p className="help">
                    Os efeitos de competência entram no mês de <b>competência</b>.
                    Diferenças entre o valor nominal e o valor efetivamente pago
                    entram no mês da baixa como efeito financeiro. Lançamentos
                    cuja linha DRE efetiva é NAO ficam fora deste relatório.
                    Clique em um valor para ver os efeitos. Total, média, AV% e
                    margens serão exibidos quando estiverem disponíveis na API.
                </p>

                {failed ? (
                    <QueryError
                        error={failed.error}
                        messages={errorMessages}
                        fallback="Não foi possível carregar a DRE operacional."
                        onRetry={() =>
                            months.forEach((month) => {
                                if (month.isError) {
                                    void month.refetch()
                                }
                            })
                        }
                    />
                ) : (
                    <div
                        className="mtx"
                        tabIndex={0}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>DRE</th>
                                    {monthIndexes.map((index) => (
                                        <th
                                            key={index}
                                            className="n"
                                        >
                                            {monthLabels[index]}
                                        </th>
                                    ))}
                                    <th
                                        className="n"
                                        title={PENDING_TITLE}
                                    >
                                        {monthIndexes.length < 12 ? "Total do período" : "Total"}
                                    </th>
                                    <th
                                        className="n med"
                                        title={PENDING_TITLE}
                                    >
                                        Média mensal
                                    </th>
                                    <th
                                        className="n"
                                        title={PENDING_TITLE}
                                    >
                                        AV%
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                {dreStructure.map((item) => {
                                    if (item.type === "line") {
                                        if (item.line === "DEP" && !hasDepreciation) {
                                            return null
                                        }

                                        const label = dreLineLabels[item.line]

                                        return (
                                            <tr
                                                key={item.line}
                                                className={item.sub ? "sub" : "grp"}
                                            >
                                                <td style={item.sub ? { paddingLeft: 28 } : undefined}>
                                                    {label}
                                                </td>

                                                {monthIndexes.map((index) => {
                                                    const cell = lineAmount(reports[index], item.line)

                                                    if (!cell) {
                                                        return (
                                                            <td
                                                                key={index}
                                                                className="n"
                                                            >
                                                                {months[index].isPending ? "…" : "—"}
                                                            </td>
                                                        )
                                                    }

                                                    const isZero = zeroPattern.test(cell.amount.trim())

                                                    return (
                                                        <td
                                                            key={index}
                                                            className={amountClass(cell.amount)}
                                                            title={`Competência ${formatExactDecimal(cell.competenceAmount)} · diferença na baixa ${formatExactDecimal(cell.settlementDifference)}`}
                                                        >
                                                            {isZero ? (
                                                                formatExactDecimalCell(cell.amount)
                                                            ) : (
                                                                <a
                                                                    href="#"
                                                                    style={{ color: "inherit", textDecoration: "none" }}
                                                                    onClick={(event) => {
                                                                        event.preventDefault()
                                                                        setEffectsTarget({
                                                                            month: monthOf(year, index),
                                                                            line: item.line,
                                                                            title: `${label} · ${monthLabels[index]}/${year}`,
                                                                        })
                                                                    }}
                                                                >
                                                                    {formatExactDecimalCell(cell.amount)}
                                                                </a>
                                                            )}
                                                        </td>
                                                    )
                                                })}

                                                <PendingCells count={PENDING_COLUMNS} medAt={1} />
                                            </tr>
                                        )
                                    }

                                    if (item.type === "subtotal") {
                                        return (
                                            <tr
                                                key={item.subtotal}
                                                className="tot"
                                            >
                                                <td>{item.label}</td>

                                                {monthIndexes.map((index) => {
                                                    const value = reports[index]?.subtotals[item.subtotal]

                                                    return (
                                                        <td
                                                            key={index}
                                                            className={amountClass(value)}
                                                        >
                                                            {value !== undefined
                                                                ? formatExactDecimalCell(value)
                                                                : months[index].isPending ? "…" : "—"}
                                                        </td>
                                                    )
                                                })}

                                                <PendingCells count={PENDING_COLUMNS} medAt={1} />
                                            </tr>
                                        )
                                    }

                                    if (item.kind === "margin") {
                                        return (
                                            <tr
                                                key={item.label}
                                                className="sub"
                                            >
                                                <td style={{ fontStyle: "italic", color: "var(--blue)" }}>
                                                    {item.label}
                                                </td>
                                                <PendingCells count={monthIndexes.length + PENDING_COLUMNS - 1} medAt={monthIndexes.length + 1} />
                                                <td />
                                            </tr>
                                        )
                                    }

                                    return (
                                        <tr
                                            key={item.label}
                                            className="grp"
                                        >
                                            <td>{item.label}</td>
                                            <PendingCells count={monthIndexes.length + PENDING_COLUMNS} medAt={monthIndexes.length + 1} />
                                        </tr>
                                    )
                                })}
                            </tbody>
                        </table>

                        {months.every((month) => month.isPending) ? (
                            <div className="empty">Carregando…</div>
                        ) : null}
                    </div>
                )}
            </div>

            {effectsTarget ? (
                <OperationalDreEffectsDialog
                    key={`${effectsTarget.month}|${effectsTarget.line}`}
                    companyId={companyId}
                    month={effectsTarget.month}
                    line={effectsTarget.line}
                    title={effectsTarget.title}
                    onClose={() => setEffectsTarget(null)}
                />
            ) : null}
        </>
    )
}
