import { useState } from "react"
import { useQueries, useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { apiErrorCode, apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import {
    getAccountingChart,
    getAccountingDre,
    getReadableAccountingSources,
    searchImportedAccounts,
} from "../api/accounting-data-api"
import { InfoButton } from "../components/accounting-info"
import { useCompanySyncStatus, useSessionUserId } from "../hooks/use-accounting-area-queries"
import { lastDayOf } from "../lib/accounting-period"
import { findAccountingScreenById, type AccountingScreen } from "../lib/accounting-screens"
import { accountingV2Access, formatDay } from "../lib/accounting-v2"
import {
    BALANCE_DEPENDENT_CHECKS,
    accountsCheck,
    chartCheck,
    diagnosticsPeriod,
    dreChecks,
    groupChecks,
    syncCheck,
    type DiagnosticCheck,
} from "../lib/base-diagnostics"
import { accountingQueryKey } from "../lib/query-keys"
import { ScreenHeader } from "./accounting-screen-page"

interface DiagScreenProps {
    screen: AccountingScreen
    competence: string
    baseText: string
    onCompetenceChange: (competence: string) => void
}

function loaded<T>(query: { isLoading: boolean; isError: boolean; error: unknown; data?: T }, enabled: boolean) {
    if (!enabled) return { state: "forbidden" as const }
    if (query.isLoading) return { state: "loading" as const }
    if (query.isError) {
        return apiErrorStatus(query.error) === 403 ? { state: "forbidden" as const } : { state: "error" as const }
    }
    return query.data === undefined ? { state: "loading" as const } : { state: "ok" as const, data: query.data }
}

const NOT_VERIFIED_TAG: Record<string, { className: string; label: string }> = {
    "no-access": { className: "tag est", label: "Sem acesso" },
    unavailable: { className: "tag est", label: "Não verificada" },
    loading: { className: "tag est", label: "Verificando" },
}

function CheckRows({ checks, onResolve }: { checks: DiagnosticCheck[]; onResolve?: (screenId: string) => void }) {
    return (
        <>
            {checks.map((check) => {
                const target = check.resolveScreenId ? findAccountingScreenById(check.resolveScreenId) : null

                return (
                    <tr key={check.id}>
                        <td className="wrapcell">
                            <b>{check.label}</b>
                        </td>
                        <td className="wrapcell">{check.detail}</td>
                        <td>
                            {check.status === "attention" ? (
                                <span className="tag pend">Requer atenção</span>
                            ) : check.status === "ok" ? (
                                <span className="tag ok">Aprovada</span>
                            ) : (
                                <span className={NOT_VERIFIED_TAG[check.status].className}>
                                    {NOT_VERIFIED_TAG[check.status].label}
                                </span>
                            )}
                        </td>
                        {onResolve && (
                            <td>
                                {target && (
                                    <button type="button" className="btn sq" onClick={() => onResolve(target.path)}>
                                        {target.label}
                                    </button>
                                )}
                            </td>
                        )}
                    </tr>
                )
            })}
        </>
    )
}

/**
 * Diagnóstico das Bases (`diag`, #530), parcial: verificações de integridade da
 * base contábil a partir de contratos oficiais. As verificações do protótipo que
 * dependem de balanço continuam "Em breve".
 */
export function DiagScreen(props: DiagScreenProps) {
    const { company, companyGrant } = useAccountingContext()
    const userId = useSessionUserId()
    const navigate = useNavigate()
    const companyId = company?.companyId ?? null
    const permissions = companyGrant?.permissions ?? []
    const access = accountingV2Access(permissions)
    const canDre = permissions.includes("DRE_VIEW")

    const [chosenRange, setRange] = useState<{ from: string; to: string } | null>(null)
    const range = diagnosticsPeriod(chosenRange, props.competence)
    const startDate = `${range.from}-01`
    const endDate = lastDayOf(range.to)
    const validRange = range.from <= range.to

    const dre = useQuery({
        queryKey: accountingQueryKey(userId, "dre", companyId, "diag", startDate, endDate),
        queryFn: () => getAccountingDre(companyId as string, startDate, endDate),
        enabled: companyId !== null && canDre && validRange,
        retry: false,
    })
    const accounts = useQuery({
        queryKey: accountingQueryKey(userId, "accounts", companyId, "diag-summary"),
        queryFn: () => searchImportedAccounts(companyId as string, { status: null, search: "", page: 0, size: 1 }),
        enabled: companyId !== null && access.chart,
        retry: false,
    })
    const readable = useQuery({
        queryKey: accountingQueryKey(userId, "sources", companyId, "readable"),
        queryFn: () => getReadableAccountingSources(companyId as string),
        enabled: companyId !== null && access.chart,
        retry: false,
    })
    const activeSources = (readable.data ?? []).filter((source) => source.active)
    const charts = useQueries({
        queries: activeSources.map((source) => ({
            queryKey: accountingQueryKey(userId, "chart", companyId, source.id, "availability"),
            queryFn: () =>
                getAccountingChart(companyId as string, { accountingSourceId: source.id, search: "", page: 0, size: 1 }),
            enabled: companyId !== null && access.chart,
            retry: false,
        })),
    })
    const sync = useCompanySyncStatus(companyId)

    if (!company) {
        return (
            <section id="p-diag" className="grid" style={{ gap: 18 }} data-handoff={props.screen.handoffSection}>
                <ScreenHeader {...props} />
                <div className="panel">
                    <div className="empty">Escolha uma empresa no filtro do cabeçalho.</div>
                </div>
            </section>
        )
    }

    // ---------------------------------------------------------------- checks
    let dreInput: Parameters<typeof dreChecks>[0]
    if (!canDre) dreInput = { state: "forbidden" }
    else if (!validRange) dreInput = { state: "error" }
    else if (dre.isLoading) dreInput = { state: "loading" }
    else if (dre.isError) {
        dreInput =
            apiErrorStatus(dre.error) === 403
                ? { state: "forbidden" }
                : apiErrorStatus(dre.error) === 422 && apiErrorCode(dre.error) === "DRE_UNSUPPORTED_NEGATIVE_AMOUNT"
                  ? { state: "negative-amount" }
                  : { state: "error" }
    } else if (dre.data) dreInput = { state: "ok", dre: dre.data }
    else dreInput = { state: "loading" }

    const accountsInput = loaded(accounts, access.chart)
    const readableInput = loaded(readable, access.chart)
    let chartInput: Parameters<typeof chartCheck>[0]
    if (readableInput.state !== "ok") chartInput = readableInput
    else if (charts.some((chart) => chart.isLoading)) chartInput = { state: "loading" }
    else if (charts.some((chart) => apiErrorStatus(chart.error) === 403)) chartInput = { state: "forbidden" }
    else if (charts.some((chart) => chart.isError)) chartInput = { state: "error" }
    else {
        chartInput = {
            state: "ok",
            sources: activeSources.map((source, index) => ({
                type: source.type,
                availability: charts[index].data?.availability ?? "IMPORTED_ACCOUNTS_ONLY",
                publishedAt: charts[index].data?.coverage.publishedAt ?? null,
            })),
        }
    }
    const syncInput = loaded(sync, true)

    const checks: DiagnosticCheck[] = [
        ...dreChecks(dreInput, startDate, endDate),
        accountsCheck(accountsInput.state === "ok" ? { state: "ok", summary: accountsInput.data.summary } : accountsInput),
        chartCheck(chartInput),
        syncCheck(
            syncInput.state === "ok"
                ? {
                      state: "ok",
                      items: syncInput.data.items.map((item) => ({ type: item.type, latestRun: item.latestRun })),
                  }
                : syncInput,
        ),
    ]
    const groups = groupChecks(checks)

    return (
        <section id="p-diag" className="grid" style={{ gap: 18 }} data-handoff={props.screen.handoffSection}>
            <ScreenHeader {...props} />

            <div className="panel">
                <div className="toolbar">
                    <div className="l">
                        <h3 style={{ margin: 0 }}>
                            Período verificado
                            <InfoButton title="Diagnóstico das Bases">
                                <p className="info-p">
                                    Verificações de integridade da base contábil importada de {company.legalName}, feitas
                                    sobre dados oficiais do FINEMP (DRE, cobertura importada, plano de contas, contas
                                    importadas e sincronizações). Nenhum valor é recalculado na tela.
                                </p>
                                <p className="info-p">
                                    As verificações do protótipo que dependem do balanço (Ativo = Passivo + PL e DRE ×
                                    balanço) ainda não têm dados publicados: Em breve.
                                </p>
                            </InfoButton>
                        </h3>
                    </div>
                    <div className="rr">
                        <label>
                            De{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.from}
                                onChange={(event) => event.target.value && setRange({ ...range, from: event.target.value })}
                            />
                        </label>
                        <label>
                            Até{" "}
                            <input
                                type="month"
                                className="search"
                                value={range.to}
                                onChange={(event) => event.target.value && setRange({ ...range, to: event.target.value })}
                            />
                        </label>
                    </div>
                </div>
                {validRange ? (
                    <p className="sub">
                        {company.legalName} · {formatDay(startDate)} a {formatDay(endDate)}. Cobertura, classificação e
                        cálculo da DRE consideram este período; plano de contas, contas pendentes e sincronização são a
                        situação atual.
                    </p>
                ) : (
                    <div className="warnbar" role="alert">
                        O início deve ser anterior ao fim.
                    </div>
                )}
            </div>

            <div className="panel">
                <h3>Inconsistências</h3>
                {groups.inconsistencies.length === 0 ? (
                    <div className="empty">
                        Nenhuma inconsistência nas verificações disponíveis
                        {groups.notVerified.length > 0 ? " (há verificações não feitas; veja abaixo)." : "."}
                    </div>
                ) : (
                    <div className="tbl-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Verificação</th>
                                    <th>Detalhe</th>
                                    <th>Situação</th>
                                    <th>Onde resolver</th>
                                </tr>
                            </thead>
                            <tbody>
                                <CheckRows checks={groups.inconsistencies} onResolve={(path) => navigate(path)} />
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            <div className="panel">
                <h3>Verificações aprovadas</h3>
                {groups.approved.length === 0 ? (
                    <div className="empty">Nenhuma verificação aprovada ainda.</div>
                ) : (
                    <div className="tbl-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Verificação</th>
                                    <th>Detalhe</th>
                                    <th>Situação</th>
                                </tr>
                            </thead>
                            <tbody>
                                <CheckRows checks={groups.approved} />
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {groups.notVerified.length > 0 && (
                <div className="panel">
                    <h3>Não verificadas</h3>
                    <div className="tbl-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Verificação</th>
                                    <th>Motivo</th>
                                    <th>Situação</th>
                                </tr>
                            </thead>
                            <tbody>
                                <CheckRows checks={groups.notVerified} />
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            <div className="panel ctb-soon" aria-disabled="true">
                <h3>
                    Verificações que dependem do balanço <span className="stc info ctb-soon-tag">Em breve</span>
                </h3>
                <ul className="ctb-issues">
                    {BALANCE_DEPENDENT_CHECKS.map((check) => (
                        <li key={check.label}>
                            <b>{check.label}</b> — {check.reason}
                        </li>
                    ))}
                </ul>
            </div>
        </section>
    )
}
