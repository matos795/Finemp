import { Fragment, useState, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { Pager } from "../../../components/finemp/pager"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { getAccountingChart, searchImportedAccounts, type AccountingChartPage } from "../api/accounting-data-api"
import { CoverageStatus } from "../components/coverage-status"
import { SourcePicker } from "../components/source-picker"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { effectiveSource, useAccountingSources } from "../hooks/use-accounting-sources"
import { useDebounced } from "../hooks/use-debounced"
import type { AccountingScreen } from "../lib/accounting-screens"
import { findAccountingScreenById } from "../lib/accounting-screens"
import { accountingV2Access, chartUniverse } from "../lib/accounting-v2"
import { accountingQueryKey } from "../lib/query-keys"
import { ScreenHeader } from "./accounting-screen-page"

const PAGE_SIZE = 50

type SourceCodes = AccountingChartPage["items"][number]["sourceCodes"]

/** Códigos crus do plano na origem, sem interpretação. */
const SOURCE_CODE_LABELS: { key: keyof SourceCodes; label: string }[] = [
    { key: "accountType", label: "accountType" },
    { key: "calculationType", label: "calculationType" },
    { key: "balanceNature", label: "balanceNature" },
    { key: "balanceControl", label: "balanceControl" },
    { key: "showInBalanceSheet", label: "showInBalanceSheet" },
    { key: "nativeDreCode", label: "nativeDreCode" },
    { key: "nativeDreAccount", label: "nativeDreAccount" },
]

interface ChartScreenProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

/**
 * Plano de Contas (#475): /accounting-chart é a autoridade do plano completo.
 * Sem snapshot de CHART publicado, mostra as contas importadas/observadas
 * (/accounting-accounts) e diz que não é o plano completo.
 */
export function ChartScreen(props: ChartScreenProps) {
    const { company, companyGrant } = useAccountingContext()
    const navigate = useNavigate()
    const userId = useSessionUserId()
    const companyId = company?.companyId ?? null
    const access = accountingV2Access(companyGrant?.permissions ?? [])
    const sources = useAccountingSources(companyId, companyGrant?.permissions ?? [])
    const [chosenSource, setChosenSource] = useState<string | null>(null)
    const sourceId = effectiveSource(sources.options, chosenSource)
    const [query, setQuery] = useState("")
    const search = useDebounced(query, 300)
    const [page, setPage] = useState(0)
    const [openCodes, setOpenCodes] = useState<string | null>(null)

    const chart = useQuery({
        queryKey: accountingQueryKey(userId, "chart", companyId, sourceId, search, page),
        queryFn: () =>
            getAccountingChart(companyId as string, {
                accountingSourceId: sourceId as string,
                search,
                page,
                size: PAGE_SIZE,
            }),
        enabled: companyId !== null && sourceId !== null && access.chart,
        placeholderData: (previous) => previous,
        retry: false,
    })

    const fullChart = chart.data?.availability === "FULL_CHART"
    const imported = useQuery({
        queryKey: accountingQueryKey(userId, "accounts", companyId, "observed", sourceId, search, page),
        queryFn: () =>
            searchImportedAccounts(companyId as string, {
                status: null,
                search,
                page,
                size: PAGE_SIZE,
                accountingSourceId: sourceId as string,
            }),
        enabled: chart.data?.availability === "IMPORTED_ACCOUNTS_ONLY",
        placeholderData: (previous) => previous,
        retry: false,
    })

    const universe = chart.data ? chartUniverse(chart.data.availability) : null
    const depara = findAccountingScreenById("depara")

    function changeSearch(value: string) {
        setQuery(value)
        setPage(0)
    }

    let body: ReactNode

    if (!company) {
        body = <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
    } else if (!access.chart) {
        body = (
            <div className="empty">
                Sem acesso ao plano de contas desta empresa. Escolher a empresa não libera os dados dela.
            </div>
        )
    } else if (sources.loading) {
        body = <div className="empty">Carregando…</div>
    } else if (sourceId === null) {
        body = (
            <div className="empty">
                Nenhuma fonte contábil desta empresa está visível para o seu acesso: ainda não há contas importadas
                e a lista de fontes exige a permissão de sincronização.
            </div>
        )
    } else if (chart.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (chart.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(chart.error) === 403
                    ? "Sem acesso ao plano de contas desta empresa."
                    : "Não foi possível carregar o plano de contas."}{" "}
                <button type="button" className="lnk" onClick={() => void chart.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else if (fullChart && chart.data) {
        const data = chart.data

        body = (
            <>
                <div className="tbl-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Código</th>
                                <th>Conta na origem</th>
                                <th>Descrição</th>
                                <th />
                            </tr>
                        </thead>
                        <tbody>
                            {data.items.map((item) => (
                                <Fragment key={item.accountId}>
                                    <tr>
                                        <td className="mono">{item.classificationCode}</td>
                                        <td className="mono">{item.sourceAccountId}</td>
                                        <td className="wrapcell">{item.description}</td>
                                        <td>
                                            <button
                                                type="button"
                                                className="btn sq"
                                                aria-expanded={openCodes === item.accountId}
                                                onClick={() =>
                                                    setOpenCodes(openCodes === item.accountId ? null : item.accountId)
                                                }
                                            >
                                                {openCodes === item.accountId ? "Ocultar códigos" : "Códigos da origem"}
                                            </button>
                                        </td>
                                    </tr>
                                    {openCodes === item.accountId && (
                                        <tr className="dp-ed">
                                            <td colSpan={4}>
                                                <dl className="ctb-meta">
                                                    {SOURCE_CODE_LABELS.map(({ key, label }) => (
                                                        <div key={key}>
                                                            <dt className="mono">{label}</dt>
                                                            <dd className="mono">
                                                                {item.sourceCodes[key] ?? (
                                                                    <span className="muted">vazio na origem</span>
                                                                )}
                                                            </dd>
                                                        </div>
                                                    ))}
                                                </dl>
                                                <p className="sub">
                                                    Códigos crus da origem contábil, exibidos sem interpretação.
                                                </p>
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            ))}
                            {data.items.length === 0 && (
                                <tr>
                                    <td colSpan={4} className="empty">
                                        {search ? "Nenhuma conta para a busca." : "O plano publicado não tem contas."}
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <Pager page={data.page} totalPages={data.totalPages} disabled={chart.isFetching} onChange={setPage} />
            </>
        )
    } else if (imported.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (imported.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(imported.error) === 403
                    ? "Sem acesso às contas importadas desta empresa."
                    : "Não foi possível carregar as contas importadas."}{" "}
                <button type="button" className="lnk" onClick={() => void imported.refetch()}>
                    Tentar de novo
                </button>
            </div>
        )
    } else if (imported.data) {
        const data = imported.data

        body = (
            <>
                <div className="tbl-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Conta</th>
                                <th>Classificação na DRE</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.items.map((account) => (
                                <tr key={account.accountId}>
                                    <td className="wrapcell">
                                        {account.description}
                                        <small className="dp-cod">{account.accountKey}</small>
                                    </td>
                                    <td>
                                        {account.status === "CLASSIFIED" ? (
                                            <span className="tag ok">Classificada</span>
                                        ) : (
                                            <span className="tag pend">Pendente</span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                            {data.items.length === 0 && (
                                <tr>
                                    <td colSpan={2} className="empty">
                                        {search ? "Nenhuma conta para a busca." : "Nenhuma conta importada ainda."}
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <Pager page={data.page} totalPages={data.totalPages} disabled={imported.isFetching} onChange={setPage} />
            </>
        )
    }

    return (
        <section id="p-plano" className="grid" style={{ gap: 18 }} data-handoff="C2.9">
            <ScreenHeader {...props} />

            <div className="panel">
                <div className="toolbar">
                    <div className="l">
                        <h3 style={{ margin: 0 }}>{universe?.title ?? "Plano de Contas"}</h3>
                        {universe && (
                            <span className={universe.complete ? "tag ok" : "tag pend"}>
                                {universe.complete ? "Plano completo" : "Não é o plano completo"}
                            </span>
                        )}
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
                        <input
                            className="search"
                            type="search"
                            placeholder="Buscar código, conta ou descrição…"
                            aria-label="Buscar no plano de contas"
                            value={query}
                            disabled={!access.chart || sourceId === null}
                            onChange={(event) => changeSearch(event.target.value)}
                        />
                    </div>
                </div>

                {company && access.chart && sourceId !== null && (
                    <CoverageStatus companyId={company.companyId} accountingSourceId={sourceId} dataset="CHART" />
                )}

                {universe && <p className="sub">{universe.note}</p>}
                {universe && !universe.complete && depara && (
                    <p className="sub">
                        A classificação dessas contas na DRE continua no{" "}
                        <button type="button" className="lnk" onClick={() => navigate(depara.path)}>
                            De-Para do Plano de Contas
                        </button>
                        .
                    </p>
                )}

                {body}
            </div>
        </section>
    )
}
