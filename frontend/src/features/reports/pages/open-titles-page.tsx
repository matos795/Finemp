import { useState } from "react"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import {
    formatExactCurrency,
    formatExactDecimalCell,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import { useOpenTitlesAging } from "../hooks/use-open-titles-aging"
import { agingBands } from "../lib/aging-bands"
import type {
    AgingDirection,
    AgingScope,
} from "../types/aging.types"

interface OpenTitlesPageProps {
    direction: AgingDirection
}

const errorMessages = {
    400: "Confira a data-base.",
    403: "Você não tem acesso aos títulos em aberto desta empresa.",
    404: "O resumo de títulos em aberto não está disponível neste ambiente.",
}

const PENDING_TITLE = "Aguardando dado da API"

/**
 * Relatórios › Contas a receber / a pagar em aberto (relRec/relPag do
 * protótipo). Total, quantidade e faixas vêm do backend. Vencidos, %,
 * prazo médio e a lista de títulos dependem de dados que a API ainda não
 * entrega e ficam pendentes, sem cálculo no frontend (PA13).
 */
export function OpenTitlesPage({
    direction,
}: OpenTitlesPageProps) {
    const { selectedCompany } =
        useCompanyAccess()

    const [asOf, setAsOf] = useState(todayIso)
    const [scope, setScope] = useState<AgingScope>("ALL")

    const aging =
        useOpenTitlesAging(selectedCompany?.id, direction, asOf, scope)

    const data = aging.data
    const isIncome = direction === "INCOME"

    const dueSoon =
        data?.bands.find((band) => band.band === "DUE_0_30")

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
                        disabled
                        title={PENDING_TITLE}
                    >
                        <option>Todas</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="ttAg">Agrupar por {isIncome ? "cliente" : "fornecedor"}</label>
                    <select
                        id="ttAg"
                        disabled
                        title={PENDING_TITLE}
                    >
                        <option>Sim</option>
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
                                {data ? formatExactCurrency(data.totalAmount) : "—"}
                            </div>
                            <div className="sub">
                                {data
                                    ? `${data.totalCount} título(s) em ${formatIsoDate(data.asOf)}`
                                    : "Carregando…"}
                            </div>
                        </div>

                        <div
                            className="card"
                            title={PENDING_TITLE}
                        >
                            <div className="lb">Vencidos</div>
                            <div className="vl">—</div>
                            <div className="sub">Em breve</div>
                        </div>

                        <div className="card">
                            <div className="lb">A vencer em 30 dias</div>
                            <div className="vl">
                                {dueSoon ? formatExactCurrency(dueSoon.amount) : "—"}
                            </div>
                            <div className="sub">
                                {dueSoon ? `${dueSoon.count} título(s)` : ""}
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
                        <div className="tblwrap">
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
                            </table>
                        </div>

                        <div className="empty">
                            A lista de títulos ainda não está disponível. O total e
                            as faixas abaixo já vêm do sistema.
                        </div>
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
                                        const band = data?.bands.find(
                                            (candidate) => candidate.band === definition.code,
                                        )

                                        return (
                                            <tr key={definition.code}>
                                                <td className={definition.overdue ? "neg" : undefined}>
                                                    {definition.label}
                                                </td>
                                                <td className="n">
                                                    {band?.count ? band.count : "–"}
                                                </td>
                                                <td className="n">
                                                    {band ? formatExactDecimalCell(band.amount) : "–"}
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
