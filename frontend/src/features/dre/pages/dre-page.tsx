import {
    useMemo,
    useState,
} from "react"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useDre } from "../hooks/use-dre"
import { DreTable } from "../components/dre-table"
import { groupDreLines } from "../lib/group-dre-lines"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { BarActions } from "../../../components/layout/bar-actions"
import { formatIsoDate } from "../../../lib/formatters/decimal"

/** Data local (YYYY-MM-DD); toISOString usaria UTC e podia virar o dia. */
function toIsoDate(
    date: Date,
) {
    const month = String(date.getMonth() + 1).padStart(2, "0")
    const day = String(date.getDate()).padStart(2, "0")

    return `${date.getFullYear()}-${month}-${day}`
}

function createInitialPeriod() {
    const today = new Date()

    const start =
        new Date(
            today.getFullYear(),
            0,
            1,
        )

    return {
        startDate: toIsoDate(start),
        endDate: toIsoDate(today),
    }
}

export function DrePage() {
    const { selectedCompany } =
        useCompanyAccess()

    const [period, setPeriod] =
        useState(createInitialPeriod)

    const [expanded, setExpanded] =
        useState<ReadonlySet<string>>(() => new Set())

    const dre =
        useDre(
            selectedCompany?.id,
            period,
        )

    // PA9: agrupamento e subtotais por grupo já eram feitos no frontend
    // antes desta fatia; mantidos sem mudança até a API fornecer os totais.
    const groups = useMemo(
        () => (dre.data ? groupDreLines(dre.data.lines) : []),
        [dre.data],
    )

    function toggle(key: string) {
        setExpanded((current) => {
            const next = new Set(current)

            if (next.has(key)) {
                next.delete(key)
            } else {
                next.add(key)
            }

            return next
        })
    }

    function expandAll() {
        setExpanded(
            new Set(
                groups.flatMap((group) => [
                    `g:${group.name}`,
                    ...group.groups.map(
                        (openGroup) => `o:${group.name}/${openGroup.name}`,
                    ),
                ]),
            ),
        )
    }

    return (
        <>
            <BarActions>
                <button
                    type="button"
                    className="icobtn"
                    title="Abrir tudo"
                    aria-label="Abrir tudo"
                    onClick={expandAll}
                >
                    <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
                        <path d="M5 4.5l5 4 5-4M5 11l5 4 5-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </button>

                <button
                    type="button"
                    className="icobtn"
                    title="Fechar tudo"
                    aria-label="Fechar tudo"
                    onClick={() => setExpanded(new Set())}
                >
                    <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
                        <path d="M5 9l5-4 5 4M5 15.5l5-4 5 4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                </button>
            </BarActions>

            <div className="filters">
                <div className="f">
                    <label htmlFor="dre-start-date">Início</label>
                    <input
                        id="dre-start-date"
                        type="date"
                        value={period.startDate}
                        onChange={(event) =>
                            setPeriod((current) => ({
                                ...current,
                                startDate: event.target.value,
                            }))
                        }
                    />
                </div>

                <div className="f">
                    <label htmlFor="dre-end-date">Fim</label>
                    <input
                        id="dre-end-date"
                        type="date"
                        value={period.endDate}
                        onChange={(event) =>
                            setPeriod((current) => ({
                                ...current,
                                endDate: event.target.value,
                            }))
                        }
                    />
                </div>
            </div>

            <div className="cards">
                <div className="card">
                    <div className="lb">Empresa</div>
                    <div className="vl" style={{ fontSize: 20 }}>
                        {selectedCompany?.name ?? "—"}
                    </div>
                </div>

                <div className="card">
                    <div className="lb">Linhas classificadas</div>
                    <div className="vl">
                        {dre.data?.lines.length ?? "—"}
                    </div>
                </div>

                <div className={dre.data?.unclassifiedAccountCount ? "card alerta" : "card"}>
                    <div className="lb">Contas sem classificação</div>
                    <div className="vl">
                        {dre.data?.unclassifiedAccountCount ?? "—"}
                    </div>
                </div>
            </div>

            <Panel
                title={`DRE contábil – ${formatIsoDate(period.startDate)} a ${formatIsoDate(period.endDate)}`}
                help="Demonstração do resultado da empresa no período selecionado, a partir da contabilidade sincronizada e da classificação DRE, DRE aberta e EBITDA."
            >
                {dre.isError ? (
                    <QueryError
                        error={dre.error}
                        messages={{
                            400: "Confira o período: a data inicial deve ser anterior ou igual à final.",
                            403: "Você não tem acesso à DRE desta empresa.",
                        }}
                        fallback="Não foi possível carregar a DRE para o período selecionado."
                        onRetry={() => dre.refetch()}
                    />
                ) : dre.isPending ? (
                    <div className="empty">Carregando…</div>
                ) : groups.length === 0 ? (
                    <div className="empty">Sem lançamentos classificados no período.</div>
                ) : (
                    <DreTable
                        groups={groups}
                        expanded={expanded}
                        onToggle={toggle}
                    />
                )}
            </Panel>
        </>
    )
}
