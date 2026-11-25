import { Fragment, useMemo, useState, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getDatasetCoverage, getOriginCatalog, searchMovements } from "../api/accounting-data-api"
import { CoverageStatus } from "../components/coverage-status"
import { MovementMetadataDetail } from "../components/movement-metadata-detail"
import { SourcePicker } from "../components/source-picker"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { effectiveSource, useAccountingSources } from "../hooks/use-accounting-sources"
import type { AccountingScreen } from "../lib/accounting-screens"
import { accountingV2Access, defaultMonthRange, formatDay, formatInstant, totalsPeriod } from "../lib/accounting-v2"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { accountingQueryKey } from "../lib/query-keys"
import { ScreenHeader } from "./accounting-screen-page"

const PAGE_SIZE = 50

function Amount({ value }: { value: string }) {
    return <td className={isNegativeDecimal(value) ? "n neg" : "n"}>{formatExactDecimal(value)}</td>
}

function currentMonth() {
    const today = new Date()

    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
}

interface MovementsScreenProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/**
 * Movimentos Contábeis (#475): /accounting-movements/search com os metadados
 * v2 (#509) num detalhe por lançamento e o catálogo técnico de origem como apoio.
 */
export function MovementsScreen(props: MovementsScreenProps) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const companyId = company?.companyId ?? null
    const access = accountingV2Access(companyGrant?.permissions ?? [])
    const sources = useAccountingSources(companyId, companyGrant?.permissions ?? [])
    const [chosenSource, setChosenSource] = useState<string | null>(null)
    const sourceId = effectiveSource(sources.options, chosenSource)
    const [chosenRange, setRange] = useState<{ from: string; to: string } | null>(null)
    const [page, setPage] = useState(0)
    const [open, setOpen] = useState<string | null>(null)
    // Mesma chave do CoverageStatus: uma só consulta para a faixa e o período inicial.
    const coverage = useQuery({
        queryKey: accountingQueryKey(userId, "coverage", companyId, sourceId, "MOVEMENTS", ""),
        queryFn: () => getDatasetCoverage(companyId as string, { accountingSourceId: sourceId as string, dataset: "MOVEMENTS" }),
        enabled: companyId !== null && sourceId !== null && access.ledger,
        retry: false,
    })
    const range = chosenRange ?? defaultMonthRange(coverage.data, currentMonth())
    const period = totalsPeriod(range.from, range.to)

    const movements = useQuery({
        queryKey: accountingQueryKey(userId, "movements", companyId, "v2", sourceId, period.startDate, period.endDate, page),
        queryFn: () =>
            searchMovements(companyId as string, {
                accountingSourceId: sourceId ?? undefined,
                startDate: period.startDate,
                endDate: period.endDate,
                page,
                size: PAGE_SIZE,
            }),
        enabled: companyId !== null && access.ledger && period.valid && !sources.loading && !coverage.isLoading,
        placeholderData: (previous) => previous,
        retry: false,
    })

    const catalog = useQuery({
        queryKey: accountingQueryKey(userId, "origin-catalog", companyId, sourceId),
        queryFn: () => getOriginCatalog(companyId as string, sourceId as string),
        enabled: companyId !== null && sourceId !== null && access.ledger,
        retry: false,
    })

    const originDescriptions = useMemo(
        () =>
            catalog.data?.availability === "AVAILABLE"
                ? new Map(catalog.data.items.map((entry) => [entry.originCode, entry.description]))
                : undefined,
        [catalog.data],
    )

    function changeRange(next: { from: string; to: string }) {
        setRange(next)
        setPage(0)
        setOpen(null)
    }

    let body: ReactNode

    if (!company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!access.ledger) {
        body = (
            <div className="empty">
                Sem acesso aos lançamentos contábeis desta empresa. Escolher a empresa não libera os dados dela.
            </div>
        )
    } else if (!period.valid) {
        body = <div className="empty">O início deve ser anterior ao fim.</div>
    } else if (movements.isLoading || sources.loading || coverage.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (movements.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(movements.error) === 403
                    ? "Sem acesso aos lançamentos contábeis desta empresa."
                    : "Não foi possível carregar os lançamentos."}{" "}
                <button type="button" className="lnk" onClick={() => void movements.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else if (movements.data) {
        const data = movements.data

        body = (
            <>
                <div className="tbl-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Data</th>
                                <th>Conta</th>
                                <th>Lançamento</th>
                                <th>Histórico</th>
                                <th className="r">Débito</th>
                                <th className="r">Crédito</th>
                                <th />
                            </tr>
                        </thead>
                        <tbody>
                            {data.items.map((item) => (
                                <Fragment key={item.id}>
                                    <tr className={open === item.id ? "dp-on" : undefined}>
                                        <td className="mono">{formatDay(item.accountingDate)}</td>
                                        <td className="wrapcell">
                                            {item.accountDescription ?? ""}
                                            <small className="dp-cod">{item.accountKey}</small>
                                        </td>
                                        <td className="mono">{item.sourceEntryKey}</td>
                                        <td className="wrapcell">{item.history ?? ""}</td>
                                        {item.side === "DEBIT" ? <Amount value={item.amount} /> : <td className="n" />}
                                        {item.side === "CREDIT" ? <Amount value={item.amount} /> : <td className="n" />}
                                        <td>
                                            <button
                                                type="button"
                                                className="btn sq"
                                                aria-expanded={open === item.id}
                                                onClick={() => setOpen(open === item.id ? null : item.id)}
                                            >
                                                {open === item.id ? "Ocultar" : "Detalhes"}
                                            </button>
                                        </td>
                                    </tr>
                                    {open === item.id && (
                                        <tr className="dp-ed">
                                            <td colSpan={7}>
                                                <MovementMetadataDetail item={item} originDescriptions={originDescriptions} />
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            ))}
                            {data.items.length === 0 && (
                                <tr>
                                    <td colSpan={7} className="empty">
                                        Sem lançamentos importados no período.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                        {data.totalItems > 0 && (
                            <tfoot>
                                <tr className="tot">
                                    <td colSpan={4}>Totais do período ({data.totalItems} lançamentos)</td>
                                    <Amount value={data.totals.debit} />
                                    <Amount value={data.totals.credit} />
                                    <td />
                                </tr>
                            </tfoot>
                        )}
                    </table>
                </div>
                <Pager page={data.page} totalPages={data.totalPages} disabled={movements.isFetching} onChange={setPage} />
            </>
        )
    }

    return (
        <section id="p-ctbmov" className="grid" style={{ gap: 18 }} data-handoff="C2.9">
            <ScreenHeader {...props} />

            <div className="panel">
                <div className="toolbar">
                    <div className="l">
                        <h3 style={{ margin: 0 }}>Lançamentos do período</h3>
                    </div>
                    <div className="rr">
                        <SourcePicker
                            options={sources.options}
                            value={sourceId}
                            onChange={(value) => {
                                setChosenSource(value)
                                setPage(0)
                            }}
                        />
                        <label>
                            De{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.from}
                                onChange={(event) => event.target.value && changeRange({ ...range, from: event.target.value })}
                            />
                        </label>
                        <label>
                            Até{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.to}
                                onChange={(event) => event.target.value && changeRange({ ...range, to: event.target.value })}
                            />
                        </label>
                    </div>
                </div>

                {company && access.ledger && sourceId !== null && (
                    <CoverageStatus companyId={company.companyId} accountingSourceId={sourceId} dataset="MOVEMENTS" />
                )}

                {body}
            </div>

            {company && access.ledger && sourceId !== null && (
                <details className="panel">
                    <summary>
                        <h3 style={{ display: "inline", margin: 0 }}>Catálogo técnico de origem</h3>
                    </summary>
                    {catalog.isLoading ? (
                        <div className="empty">Carregando…</div>
                    ) : catalog.isError ? (
                        <div className="empty">
                            {apiErrorStatus(catalog.error) === 403
                                ? "Sem acesso ao catálogo de origem desta empresa."
                                : "Não foi possível carregar o catálogo de origem."}
                        </div>
                    ) : catalog.data?.availability !== "AVAILABLE" ? (
                        <div className="empty">O catálogo técnico de origem ainda não foi publicado para esta fonte.</div>
                    ) : (
                        <>
                            <p className="sub">
                                Códigos e descrições técnicas da origem contábil, publicados em{" "}
                                {formatInstant(catalog.data.publishedAt ?? "")}. Exibidos sem interpretação.
                            </p>
                            <div className="tbl-wrap">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Código</th>
                                            <th>Descrição técnica</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {catalog.data.items.map((entry) => (
                                            <tr key={entry.originCode}>
                                                <td className="mono">{entry.originCode}</td>
                                                <td className="wrapcell">{entry.description}</td>
                                            </tr>
                                        ))}
                                        {catalog.data.items.length === 0 && (
                                            <tr>
                                                <td colSpan={2} className="empty">
                                                    O catálogo publicado está vazio.
                                                </td>
                                            </tr>
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}
                </details>
            )}
        </section>
    )
}
