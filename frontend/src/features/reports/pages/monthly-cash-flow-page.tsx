import {
    Fragment,
    useState,
    type ReactNode,
} from "react"
import { QueryError } from "../../../components/finemp/query-error"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { CashAmountCell } from "../components/cash-amount-cell"
import {
    MonthlyCashCategoryRows,
    type CategoryCellTarget,
} from "../components/monthly-cash-category-rows"
import { MonthlyCashTitlesDialog } from "../components/monthly-cash-titles-dialog"
import { useMonthlyCashTotals } from "../hooks/use-cash-totals"
import { monthLabels } from "../lib/calendar-labels"
import type {
    CashActivity,
    CashDirection,
    CashState,
    MonthlyCashDetailFilter,
} from "../types/cash-totals.types"

const PENDING_TITLE = "Aguardando dado da API"

/** Rótulos do protótipo (NOMES do renderMensal). */
const activities: {
    activity: CashActivity
    names: [string, string, string]
}[] = [
    {
        activity: "OPERATING",
        names: ["FLUXO OPERACIONAL", "Recebimentos operacionais", "(−) Pagamentos operacionais"],
    },
    {
        activity: "INVESTING",
        names: ["FLUXO DE INVESTIMENTO", "Entradas (venda de bens, resgates)", "(−) Investimentos (capex)"],
    },
    {
        activity: "FINANCING",
        names: ["FLUXO DE FINANCIAMENTO", "Entradas (empréstimos, aportes)", "(−) Saídas (parcelas, lucros, retiradas)"],
    },
]

const directions: CashDirection[] = ["INCOME", "EXPENSE"]

const errorMessages = {
    400: "Confira o ano.",
    403: "Você não tem acesso ao fluxo de caixa desta empresa.",
    404: "O fluxo mensal não está disponível neste ambiente.",
}

/** Colunas Total e Média mensal: ainda sem dado por atividade na API. */
const PENDING_COLUMNS = 2

function amountKey(state: CashState, direction: CashDirection) {
    if (state === "REALIZED") {
        return direction === "INCOME" ? "realIncome" : "realExpense"
    }

    return direction === "INCOME" ? "openIncome" : "openExpense"
}

function PendingCells({ count }: { count: number }) {
    return (
        <>
            {Array.from({ length: count }, (_, index) => (
                <td
                    key={index}
                    className="n"
                    title={PENDING_TITLE}
                >
                    —
                </td>
            ))}
        </>
    )
}

/**
 * Fluxo de caixa › Fluxo mensal. Recebimentos e pagamentos por atividade
 * (operacional, investimento e financiamento), categorias e títulos vêm
 * da API. Saldos, linhas de caixa líquido, total e média por atividade,
 * visão "realizado + em aberto", fixo × variável, indicadores e gráficos
 * dependem de dados que a API ainda não entrega e não são calculados
 * aqui (PA4).
 */
export function MonthlyCashFlowPage() {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id

    const today = todayIso()
    const currentYear = Number(today.slice(0, 4))
    const currentMonth = Number(today.slice(5, 7))

    const [year, setYear] = useState(currentYear)
    const [state, setState] = useState<CashState>("REALIZED")
    const [firstMonth, setFirstMonth] = useState(0)
    const [lastMonth, setLastMonth] = useState(11)
    const [open, setOpen] = useState<Record<string, boolean>>({})
    const [titlesTarget, setTitlesTarget] = useState<{
        filter: MonthlyCashDetailFilter
        title: string
    } | null>(null)

    const totals =
        useMonthlyCashTotals(companyId, year)

    const years = Array.from({ length: 6 }, (_, index) => currentYear + 1 - index)

    const monthIndexes = Array.from(
        { length: lastMonth - firstMonth + 1 },
        (_, index) => firstMonth + index,
    )

    const columns = monthIndexes.length

    function toggle(key: string) {
        setOpen((previous) => ({ ...previous, [key]: !previous[key] }))
    }

    function setAll(value: boolean) {
        const next: Record<string, boolean> = {}

        activities.forEach(({ activity }) =>
            directions.forEach((direction) => {
                next[`${activity}|${direction}`] = value
            }),
        )

        setOpen(next)
    }

    function activityValue(
        activity: CashActivity,
        direction: CashDirection,
        monthIndex: number,
    ) {
        return totals.data?.activityMonths.find(
            (row) => row.activity === activity && row.month === monthIndex + 1,
        )?.[amountKey(state, direction)]
    }

    function openTitles(
        activity: CashActivity,
        direction: CashDirection,
        target: CategoryCellTarget,
    ) {
        const month = `${String(year).padStart(4, "0")}-${String(target.monthIndex + 1).padStart(2, "0")}`

        setTitlesTarget({
            filter: {
                month,
                direction,
                state,
                activity,
                categoryId: target.categoryId,
            },
            title: `${target.label} · ${monthLabels[target.monthIndex]}/${year}`,
        })
    }

    const pendingRow = (label: ReactNode) => (
        <tr className="tot">
            <td>{label}</td>
            <PendingCells count={columns + PENDING_COLUMNS} />
        </tr>
    )

    const note = state === "REALIZED"
        ? "Somente o que já foi pago ou recebido, pela data do pagamento."
        : "Somente títulos em aberto, pelo vencimento."

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="fcCR">Centro de resultado</label>
                    <select
                        id="fcCR"
                        disabled
                        title={PENDING_TITLE}
                    >
                        <option>Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="mAno">Ano</label>
                    <select
                        id="mAno"
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
                    <label>Valores</label>
                    <div className="tabs">
                        <button
                            type="button"
                            className="pill"
                            disabled
                            title={PENDING_TITLE}
                        >
                            Realizado + em aberto
                        </button>
                        <button
                            type="button"
                            className={state === "REALIZED" ? "pill on" : "pill"}
                            onClick={() => setState("REALIZED")}
                        >
                            Só realizado
                        </button>
                        <button
                            type="button"
                            className={state === "OPEN" ? "pill on" : "pill"}
                            onClick={() => setState("OPEN")}
                        >
                            Só em aberto
                        </button>
                    </div>
                </div>

                <div className="f">
                    <label>Abrir as linhas por</label>
                    <div className="tabs">
                        <button
                            type="button"
                            className="pill on"
                        >
                            Grupo e categoria
                        </button>
                        <button
                            type="button"
                            className="pill"
                            disabled
                            title={PENDING_TITLE}
                        >
                            Fixo e variável
                        </button>
                    </div>
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
                    onClick={() => setAll(true)}
                >
                    Abrir tudo
                </button>
                <button
                    type="button"
                    className="btn sec sm"
                    onClick={() => setAll(false)}
                >
                    Fechar tudo
                </button>
                <button
                    type="button"
                    className="btn sec sm"
                    onClick={() => window.print()}
                >
                    Imprimir / PDF
                </button>
            </div>

            <div className="cards">
                {[
                    ["Caixa operacional", "gerado pela operação"],
                    ["Investimentos", "compra e venda de bens"],
                    ["Fluxo de caixa livre", "operacional + investimento"],
                    ["Financiamentos e sócios", "empréstimos, aportes, lucros"],
                ].map(([label, sub]) => (
                    <div
                        key={label}
                        className="card"
                        title={PENDING_TITLE}
                    >
                        <div className="lb">
                            {label === "Caixa operacional" ? `${label} ${year}` : label}
                        </div>
                        <div className="vl">—</div>
                        <div className="sub">{sub}</div>
                    </div>
                ))}
            </div>

            <div className="panel">
                <h2>Fluxo de caixa {year} – operacional, investimento e financiamento</h2>
                <p className="help">
                    {note} A atividade de cada categoria é definida em Cadastros ›
                    Plano de contas. Clique em ▸ para ver as categorias e em um
                    valor para ver os títulos. Saldos, caixa líquido por
                    atividade, total, média e a visão realizado + em aberto
                    serão exibidos quando estiverem disponíveis na API.
                </p>

                {totals.isError ? (
                    <QueryError
                        error={totals.error}
                        messages={errorMessages}
                        fallback="Não foi possível carregar o fluxo mensal."
                        onRetry={() => totals.refetch()}
                    />
                ) : (
                    <div
                        className="mtx"
                        tabIndex={0}
                        style={totals.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Fluxo de caixa</th>
                                    {monthIndexes.map((index) => (
                                        <th
                                            key={index}
                                            className="n"
                                        >
                                            {monthLabels[index]}
                                            {year === currentYear && index + 1 === currentMonth ? " •" : ""}
                                        </th>
                                    ))}
                                    <th
                                        className="n"
                                        title={PENDING_TITLE}
                                    >
                                        {columns < 12 ? "Total do período" : "Total"}
                                    </th>
                                    <th
                                        className="n med"
                                        title={PENDING_TITLE}
                                    >
                                        Média mensal
                                    </th>
                                </tr>
                            </thead>

                            <tbody>
                                {pendingRow("Saldo inicial")}

                                {activities.map(({ activity, names }) => (
                                    <Fragment key={activity}>
                                        {pendingRow(
                                            <span
                                                style={{ color: "var(--gold)" }}
                                                title="Entradas menos saídas desta atividade"
                                            >
                                                {names[0]}
                                            </span>,
                                        )}

                                        {directions.map((direction, directionIndex) => {
                                            const key = `${activity}|${direction}`

                                            return (
                                                <Fragment key={direction}>
                                                    <tr className="grp">
                                                        <td>
                                                            <span
                                                                className="tog"
                                                                role="button"
                                                                tabIndex={0}
                                                                onClick={() => toggle(key)}
                                                                onKeyDown={(event) => {
                                                                    if (event.key === "Enter" || event.key === " ") {
                                                                        event.preventDefault()
                                                                        toggle(key)
                                                                    }
                                                                }}
                                                            >
                                                                {open[key] ? "▾" : "▸"}
                                                            </span>
                                                            {names[1 + directionIndex]}
                                                        </td>
                                                        {monthIndexes.map((index) => (
                                                            <CashAmountCell
                                                                key={index}
                                                                value={activityValue(activity, direction, index)}
                                                            />
                                                        ))}
                                                        <PendingCells count={PENDING_COLUMNS} />
                                                    </tr>

                                                    {open[key] ? (
                                                        <MonthlyCashCategoryRows
                                                            companyId={companyId}
                                                            year={year}
                                                            direction={direction}
                                                            state={state}
                                                            activity={activity}
                                                            monthIndexes={monthIndexes}
                                                            pendingColumns={PENDING_COLUMNS}
                                                            onOpenTitles={(target) =>
                                                                openTitles(activity, direction, target)
                                                            }
                                                        />
                                                    ) : null}
                                                </Fragment>
                                            )
                                        })}

                                        {activity === "INVESTING"
                                            ? pendingRow("= Fluxo de caixa livre (operacional + investimento)")
                                            : null}
                                    </Fragment>
                                ))}

                                {pendingRow("= Variação do caixa")}
                                {pendingRow("Saldo final")}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {titlesTarget ? (
                <MonthlyCashTitlesDialog
                    key={JSON.stringify(titlesTarget.filter)}
                    companyId={companyId}
                    filter={titlesTarget.filter}
                    title={titlesTarget.title}
                    onClose={() => setTitlesTarget(null)}
                />
            ) : null}
        </>
    )
}
