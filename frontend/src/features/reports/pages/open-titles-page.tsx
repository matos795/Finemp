import { useState } from "react"
import { Pager } from "../../../components/finemp/pager"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import {
    formatExactCurrency,
    formatExactDecimalCell,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { OpenTitlePartnerGroup } from "../components/open-title-partner-group"
import { OpenTitleRow } from "../components/open-title-row"
import {
    useOpenTitlePartnerTotals,
    useOpenTitlesList,
} from "../hooks/use-open-titles"
import { useOpenTitlesAging } from "../hooks/use-open-titles-aging"
import {
    agingBands,
    isOverdueBand,
} from "../lib/aging-bands"
import type {
    AgingBandCode,
    AgingDirection,
    AgingScope,
    OpenTitlesFilter,
} from "../types/aging.types"

interface OpenTitlesPageProps {
    direction: AgingDirection
}

const errorMessages = {
    400: "Confira a data-base.",
    403: "Você não tem acesso aos títulos em aberto desta empresa.",
    404: "O resumo de títulos em aberto não está disponível neste ambiente.",
}

const listErrorMessages = {
    400: "Confira os filtros.",
    403: "Você não tem acesso à lista de títulos desta empresa.",
    404: "A lista de títulos em aberto não está disponível neste ambiente.",
}

const PENDING_TITLE = "Aguardando dado da API"

const GROUPS_PAGE_SIZE = 10
const TITLES_PAGE_SIZE = 50

/**
 * Relatórios › Contas a receber / a pagar em aberto (relRec/relPag do
 * protótipo). Totais, vencidos, a vencer em 30 dias, faixas, subtotais por
 * parceiro, dias de atraso e a lista vêm do backend. Percentuais, prazo
 * médio, centro de resultado e exportação seguem pendentes (PA13).
 */
export function OpenTitlesPage({
    direction,
}: OpenTitlesPageProps) {
    const { selectedCompany } =
        useCompanyAccess()

    const companyId = selectedCompany?.id

    const [asOf, setAsOf] = useState(todayIso)
    const [scope, setScope] = useState<AgingScope>("ALL")
    const [band, setBand] = useState<AgingBandCode | "">("")
    const [grouped, setGrouped] = useState(true)

    const filter: OpenTitlesFilter = {
        direction,
        asOf,
        scope,
        band: band || undefined,
    }

    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ ...filter, grouped }),
    )

    const aging =
        useOpenTitlesAging(companyId, direction, asOf, scope)

    const groups = useOpenTitlePartnerTotals(
        companyId,
        filter,
        page,
        GROUPS_PAGE_SIZE,
        grouped,
    )

    const list = useOpenTitlesList(
        companyId,
        filter,
        page,
        TITLES_PAGE_SIZE,
        !grouped,
    )

    const groupItems =
        grouped ? groups.data?.items ?? [] : []

    const data = aging.data
    const isIncome = direction === "INCOME"
    const partnerLabel = isIncome ? "cliente" : "fornecedor"

    const selectedBand =
        band ? data?.bands.find((candidate) => candidate.band === band) : undefined

    // Com faixa escolhida, o protótipo mostra o total e os vencidos da faixa.
    // Os números são os da própria faixa no backend; nada é somado aqui.
    const totalAmount =
        band ? selectedBand?.amount : data?.totalAmount

    const totalCount =
        band ? selectedBand?.count : data?.totalCount

    const overdueAmount = !data
        ? undefined
        : !band
            ? data.overdueAmount
            : isOverdueBand(band)
                ? selectedBand?.amount
                : "0.00"

    const hasOverdue =
        overdueAmount !== undefined && /[1-9]/.test(overdueAmount)

    const active = grouped ? groups : list

    const isEmpty = grouped
        ? groups.data?.items.length === 0
        : list.data?.items.length === 0

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="ttBase">Posição em (data-base)</label>
                    <input
                        type="date"
                        id="ttBase"
                        value={asOf}
                        onChange={(event) =>
                            setAsOf(event.target.value || todayIso())
                        }
                    />
                </div>

                <div className="f">
                    <label htmlFor="ttAbr">Abrangência</label>
                    <select
                        id="ttAbr"
                        value={scope}
                        onChange={(event) =>
                            setScope(event.target.value as AgingScope)
                        }
                    >
                        <option value="ALL">Todos os títulos em aberto</option>
                        <option value="COMPETENCE">Só competência até a data-base</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="ttCR">Centro de resultado</label>
                    <select
                        id="ttCR"
                        disabled
                        title={PENDING_TITLE}
                    >
                        <option>Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="ttFx">Faixa</label>
                    <select
                        id="ttFx"
                        value={band}
                        onChange={(event) =>
                            setBand(event.target.value as AgingBandCode | "")
                        }
                    >
                        <option value="">Todas</option>
                        {agingBands.map((definition) => (
                            <option
                                key={definition.code}
                                value={definition.code}
                            >
                                {definition.label}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="ttAg">Agrupar por {partnerLabel}</label>
                    <select
                        id="ttAg"
                        value={grouped ? "1" : "0"}
                        onChange={(event) =>
                            setGrouped(event.target.value === "1")
                        }
                    >
                        <option value="1">Sim</option>
                        <option value="0">Não, por vencimento</option>
                    </select>
                </div>

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

            {aging.isError ? (
                <QueryError
                    error={aging.error}
                    messages={errorMessages}
                    fallback="Não foi possível carregar os títulos em aberto."
                    onRetry={() => aging.refetch()}
                />
            ) : (
                <div style={aging.isPlaceholderData ? { opacity: 0.6 } : undefined}>
                    <div className="cards">
                        <div className="card">
                            <div className="lb">Total em aberto</div>
                            <div className="vl">
                                {totalAmount !== undefined ? formatExactCurrency(totalAmount) : "—"}
                            </div>
                            <div className="sub">
                                {data && totalCount !== undefined
                                    ? `${totalCount} título(s) em ${formatIsoDate(data.asOf)}`
                                    : "Carregando…"}
                            </div>
                        </div>

                        <div className={hasOverdue ? "card alerta" : "card"}>
                            <div className="lb">Vencidos</div>
                            <div className={hasOverdue ? "vl neg" : "vl"}>
                                {overdueAmount !== undefined ? formatExactCurrency(overdueAmount) : "—"}
                            </div>
                            <div
                                className="sub"
                                title={PENDING_TITLE}
                            >
                                — do total
                            </div>
                        </div>

                        <div className="card">
                            <div className="lb">A vencer em 30 dias</div>
                            <div className="vl">
                                {data ? formatExactCurrency(data.dueWithin30Amount) : "—"}
                            </div>
                            <div className="sub">
                                {data ? `${data.dueWithin30Count} título(s)` : ""}
                            </div>
                        </div>

                        <div
                            className="card"
                            title={PENDING_TITLE}
                        >
                            <div className="lb">Prazo médio a vencer</div>
                            <div className="vl">—</div>
                            <div className="sub">ponderado pelo valor · em breve</div>
                        </div>
                    </div>

                    <div
                        className="panel"
                        style={{ padding: 0 }}
                    >
                        {active.isError ? (
                            <QueryError
                                error={active.error}
                                messages={listErrorMessages}
                                fallback="Não foi possível carregar a lista de títulos."
                                onRetry={() => active.refetch()}
                            />
                        ) : (
                            <>
                                <div
                                    className="tblwrap"
                                    style={active.isPlaceholderData ? { opacity: 0.6 } : undefined}
                                >
                                    <table>
                                        <thead>
                                            <tr>
                                                <th>{isIncome ? "Cliente" : "Fornecedor"}</th>
                                                <th>Descrição</th>
                                                <th>Documento</th>
                                                <th>Comp.</th>
                                                <th>Vencimento</th>
                                                <th className="n">Dias atraso</th>
                                                <th>Centro</th>
                                                <th className="n">Valor</th>
                                                <th />
                                            </tr>
                                        </thead>

                                        <tbody>
                                            {grouped
                                                ? groupItems.map((group) => (
                                                    <OpenTitlePartnerGroup
                                                        key={group.partnerId ?? "_sem"}
                                                        companyId={companyId}
                                                        filter={filter}
                                                        group={group}
                                                        isIncome={isIncome}
                                                    />
                                                ))
                                                : list.data?.items.map((title) => (
                                                    <OpenTitleRow
                                                        key={title.id}
                                                        title={title}
                                                        isIncome={isIncome}
                                                    />
                                                ))}
                                        </tbody>

                                        <tfoot>
                                            <tr>
                                                <td colSpan={7}>Total</td>
                                                <td className="n">
                                                    {totalAmount !== undefined ? formatExactDecimalCell(totalAmount) : "—"}
                                                </td>
                                                <td />
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>

                                {isEmpty ? (
                                    <div className="empty">
                                        Nenhum título em aberto com esses filtros.
                                    </div>
                                ) : null}

                                {active.isPending && active.fetchStatus !== "idle" ? (
                                    <div className="empty">Carregando…</div>
                                ) : null}

                                <Pager
                                    page={page}
                                    totalPages={active.data?.totalPages ?? 0}
                                    disabled={active.isFetching}
                                    onChange={setPage}
                                />
                            </>
                        )}
                    </div>

                    <Panel title="Por faixa de vencimento">
                        <div className="tblwrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Faixa</th>
                                        <th className="n">Títulos</th>
                                        <th className="n">Valor</th>
                                        <th
                                            className="n"
                                            title={PENDING_TITLE}
                                        >
                                            %
                                        </th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {agingBands.map((definition) => {
                                        const row = data?.bands.find(
                                            (candidate) => candidate.band === definition.code,
                                        )

                                        return (
                                            <tr key={definition.code}>
                                                <td className={definition.overdue ? "neg" : undefined}>
                                                    <a
                                                        href="#"
                                                        style={{ color: "inherit" }}
                                                        onClick={(event) => {
                                                            event.preventDefault()
                                                            setBand(band === definition.code ? "" : definition.code)
                                                        }}
                                                    >
                                                        {definition.label}
                                                    </a>
                                                </td>
                                                <td className="n">
                                                    {row?.count ? row.count : "–"}
                                                </td>
                                                <td className="n">
                                                    {row ? formatExactDecimalCell(row.amount) : "–"}
                                                </td>
                                                <td className="n">—</td>
                                            </tr>
                                        )
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </Panel>
                </div>
            )}
        </>
    )
}

