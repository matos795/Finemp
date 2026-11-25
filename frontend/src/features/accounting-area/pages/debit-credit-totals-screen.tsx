import { useState, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getDebitCreditTotals, type DebitCreditGranularity } from "../api/accounting-data-api"
import { CoverageStatus } from "../components/coverage-status"
import { SourcePicker } from "../components/source-picker"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { effectiveSource, useAccountingSources } from "../hooks/use-accounting-sources"
import { useDebounced } from "../hooks/use-debounced"
import type { AccountingScreen } from "../lib/accounting-screens"
import { accountingV2Access, formatDay, totalsPeriod } from "../lib/accounting-v2"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { accountingQueryKey } from "../lib/query-keys"
import { ScreenHeader } from "./accounting-screen-page"

const PAGE_SIZE = 100

const GRANULARITIES: { key: DebitCreditGranularity; label: string }[] = [
    { key: "DAILY", label: "Diário" },
    { key: "MONTHLY", label: "Mensal" },
]

function currentMonth() {
    const today = new Date()

    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
}

function Money({ value }: { value: string }) {
    return <td className={isNegativeDecimal(value) ? "n neg" : "n"}>{value}</td>
}

interface TotalsScreenProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/**
 * Totais de Débito e Crédito (#475): /accounting-debit-credit-totals (#509),
 * diário ou mensal, como publicados pela origem. Nada é somado nem acumulado.
 */
export function DebitCreditTotalsScreen(props: TotalsScreenProps) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const companyId = company?.companyId ?? null
    const access = accountingV2Access(companyGrant?.permissions ?? [])
    const sources = useAccountingSources(companyId, companyGrant?.permissions ?? [])
    const [chosenSource, setChosenSource] = useState<string | null>(null)
    const sourceId = effectiveSource(sources.options, chosenSource)
    const [granularity, setGranularity] = useState<DebitCreditGranularity>("DAILY")
    const [range, setRange] = useState(() => ({ from: currentMonth(), to: currentMonth() }))
    const [establishmentInput, setEstablishmentInput] = useState("")
    const [accountInput, setAccountInput] = useState("")
    const establishmentCode = useDebounced(establishmentInput.trim(), 300)
    const sourceAccountId = useDebounced(accountInput.trim(), 300)
    const [page, setPage] = useState(0)
    const period = totalsPeriod(range.from, range.to)

    const totals = useQuery({
        queryKey: accountingQueryKey(
            userId, "totals", companyId, sourceId, granularity, period.startDate, period.endDate,
            establishmentCode, sourceAccountId, page,
        ),
        queryFn: () =>
            getDebitCreditTotals(companyId as string, {
                accountingSourceId: sourceId as string,
                granularity,
                startDate: period.startDate,
                endDate: period.endDate,
                establishmentCode,
                sourceAccountId,
                page,
                size: PAGE_SIZE,
            }),
        enabled: companyId !== null && sourceId !== null && access.ledger && period.valid,
        placeholderData: (previous) => previous,
        retry: false,
    })

    function resetPage() {
        setPage(0)
    }

    let body: ReactNode

    if (!company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!access.ledger) {
        body = (
            <div className="empty">
                Sem acesso aos totais contábeis desta empresa. Escolher a empresa não libera os dados dela.
            </div>
        )
    } else if (sources.loading) {
        body = <div className="empty">Carregando…</div>
    } else if (sourceId === null) {
        body = (
            <div className="empty">
                Nenhuma fonte contábil desta empresa está visível para o seu acesso: a lista de fontes exige a
                permissão de sincronização ou de plano de contas.
            </div>
        )
    } else if (!period.valid) {
        body = <div className="empty">O início deve ser anterior ao fim.</div>
    } else if (totals.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (totals.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(totals.error) === 403
                    ? "Sem acesso aos totais contábeis desta empresa."
                    : "Não foi possível carregar os totais de débito e crédito."}{" "}
                <button type="button" className="lnk" onClick={() => void totals.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else if (totals.data) {
        const data = totals.data

        body = (
            <>
                <div className="tbl-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>{data.granularity === "MONTHLY" ? "Mês" : "Dia"}</th>
                                <th>Estabelecimento</th>
                                <th>Conta na origem</th>
                                <th>Tipo do lançamento</th>
                                <th className="r">Débito</th>
                                <th className="r">Crédito</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.items.map((row) => (
                                <tr key={`${row.period}|${row.establishmentCode}|${row.sourceAccountId}|${row.entryTypeCode}`}>
                                    <td className="mono">{formatDay(row.period)}</td>
                                    <td className="mono">{row.establishmentCode}</td>
                                    <td className="mono">{row.sourceAccountId}</td>
                                    <td className="mono">{row.entryTypeCode}</td>
                                    <Money value={formatExactDecimal(row.debitTotal)} />
                                    <Money value={formatExactDecimal(row.creditTotal)} />
                                </tr>
                            ))}
                            {data.items.length === 0 && (
                                <tr>
                                    <td colSpan={6} className="empty">
                                        Nenhum total importado para os filtros.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <Pager page={data.page} totalPages={data.totalPages} disabled={totals.isFetching} onChange={setPage} />
            </>
        )
    }

    return (
        <section id="p-ctbtot" className="grid" style={{ gap: 18 }} data-handoff="C2.9">
            <ScreenHeader {...props} />

            <div className="panel">
                <div className="toolbar">
                    <div className="l">
                        <h3 style={{ margin: 0 }}>Totais do período</h3>
                        <div className="tabs" role="group" aria-label="Agrupamento">
                            {GRANULARITIES.map((item) => (
                                <button
                                    key={item.key}
                                    type="button"
                                    className={item.key === granularity ? "pill on" : "pill"}
                                    aria-pressed={item.key === granularity}
                                    onClick={() => {
                                        setGranularity(item.key)
                                        resetPage()
                                    }}
                                >
                                    {item.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div className="rr">
                        <SourcePicker
                            options={sources.options}
                            value={sourceId}
                            onChange={(value) => {
                                setChosenSource(value)
                                resetPage()
                            }}
                        />
                        <label>
                            De{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.from}
                                onChange={(event) => {
                                    if (event.target.value) {
                                        setRange({ ...range, from: event.target.value })
                                        resetPage()
                                    }
                                }}
                            />
                        </label>
                        <label>
                            Até{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.to}
                                onChange={(event) => {
                                    if (event.target.value) {
                                        setRange({ ...range, to: event.target.value })
                                        resetPage()
                                    }
                                }}
                            />
                        </label>
                        <input
                            className="search"
                            type="search"
                            placeholder="Estabelecimento (opcional)"
                            aria-label="Estabelecimento"
                            value={establishmentInput}
                            onChange={(event) => {
                                setEstablishmentInput(event.target.value)
                                resetPage()
                            }}
                        />
                        <input
                            className="search"
                            type="search"
                            placeholder="Conta na origem (opcional)"
                            aria-label="Conta na origem"
                            value={accountInput}
                            onChange={(event) => {
                                setAccountInput(event.target.value)
                                resetPage()
                            }}
                        />
                    </div>
                </div>

                <p className="sub">
                    Totais de débito e crédito de cada {granularity === "MONTHLY" ? "mês" : "dia"}, por estabelecimento,
                    conta e tipo de lançamento, como publicados pela origem contábil. Não são saldo: nada é somado
                    nem acumulado aqui.
                </p>

                {company && access.ledger && sourceId !== null && (
                    <CoverageStatus
                        companyId={company.companyId}
                        accountingSourceId={sourceId}
                        dataset={granularity === "MONTHLY" ? "MONTHLY_TOTALS" : "DAILY_TOTALS"}
                    />
                )}

                {body}
            </div>
        </section>
    )
}
