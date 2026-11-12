import { useState } from "react"
import { Pager } from "../../../components/finemp/pager"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import {
    formatExactDecimalCell,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { todayIso } from "../../../lib/formatters/local-date"
import { useAllBankAccounts } from "../../bank-accounts/hooks/use-bank-accounts"
import { useCompanyAccess } from "../../companies/company-access-context"
import { FinancialEntrySettlementDialog } from "../../financial-entries/components/financial-entry-settlement-dialog"
import { useOpenTitlesList } from "../hooks/use-open-titles"
import { formatCompetence } from "../lib/calendar-labels"
import {
    canSettleOpenTitle,
    settlementTargetFromOpenTitle,
} from "../lib/open-title-settlement"
import type {
    AgingDirection,
    OpenTitle,
    OpenTitlesFilter,
} from "../types/aging.types"

const PAGE_SIZE = 50

const errors = {
    400: "Confira a data-base.",
    403: "Você não tem acesso aos títulos desta empresa.",
    404: "A agenda de pagar e receber não está disponível neste ambiente.",
}

/**
 * Fluxo de caixa › Pagar e receber.
 *
 * Agenda operacional: reutiliza a leitura canônica de títulos em aberto,
 * ordenada pelo backend por vencimento. Não calcula aging nem cria uma
 * segunda regra de baixa; Receber/Pagar usa o mesmo comando de Lançamentos.
 */
export function CashFlowSchedulePage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id

    const [direction, setDirection] = useState<AgingDirection>("EXPENSE")
    const [asOf, setAsOf] = useState(todayIso)
    const [settling, setSettling] =
        useState<{ companyId: string, title: OpenTitle } | null>(null)

    const filter: OpenTitlesFilter = {
        direction,
        asOf,
        scope: "ALL",
    }

    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ direction, asOf }),
    )

    const titles = useOpenTitlesList(
        companyId,
        filter,
        page,
        PAGE_SIZE,
        true,
    )

    const settlingHere =
        settling?.companyId === companyId &&
        settling.title.direction === direction
            ? settling
            : null

    const bankAccounts =
        useAllBankAccounts(settlingHere ? companyId : undefined)

    const onSettle = hasPermission("FINANCIAL_ENTRY_MANAGE")
        ? (title: OpenTitle) => {
            if (companyId && canSettleOpenTitle(title, hasPermission)) {
                setSettling({ companyId, title })
            }
        }
        : undefined

    const isIncome = direction === "INCOME"

    return (
        <>
            <Panel
                title="Pagar e receber"
                help={
                    <>
                        Agenda dos títulos em aberto por vencimento. Use <b>Receber</b> ou{" "}
                        <b>Pagar</b> para registrar a baixa sem sair desta tela.
                    </>
                }
            >
                <div className="tools">
                    <div className="tabs">
                        <button
                            type="button"
                            className={direction === "EXPENSE" ? "pill on" : "pill"}
                            onClick={() => setDirection("EXPENSE")}
                        >
                            A pagar
                        </button>
                        <button
                            type="button"
                            className={direction === "INCOME" ? "pill on" : "pill"}
                            onClick={() => setDirection("INCOME")}
                        >
                            A receber
                        </button>
                    </div>

                    <div className="f">
                        <label htmlFor="scheduleAsOf">Posição em</label>
                        <input
                            id="scheduleAsOf"
                            type="date"
                            value={asOf}
                            onChange={(event) =>
                                setAsOf(event.target.value || todayIso())
                            }
                        />
                    </div>
                </div>
            </Panel>

            <div
                className="panel"
                style={{
                    padding: 0,
                    ...(titles.isPlaceholderData ? { opacity: 0.6 } : {}),
                }}
            >
                {titles.isError ? (
                    <QueryError
                        error={titles.error}
                        messages={errors}
                        fallback="Não foi possível carregar a agenda financeira."
                        onRetry={() => titles.refetch()}
                    />
                ) : (
                    <>
                        <div className="tblwrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Descrição</th>
                                        <th>Documento</th>
                                        <th>Comp.</th>
                                        <th>Vencimento</th>
                                        <th className="n">Dias atraso</th>
                                        <th className="n">Valor</th>
                                        <th />
                                    </tr>
                                </thead>
                                <tbody>
                                    {titles.data?.items.map((title) => (
                                        <tr key={title.id}>
                                            <td style={{ fontSize: 13 }}>{title.description}</td>
                                            <td style={{ fontSize: 13 }}>{title.documentNumber}</td>
                                            <td>{formatCompetence(title.competence)}</td>
                                            <td>{formatIsoDate(title.dueDate)}</td>
                                            <td className={title.daysLate > 0 ? "n neg" : "n"}>
                                                {title.daysLate > 0 ? title.daysLate : "–"}
                                            </td>
                                            <td className="n">
                                                {formatExactDecimalCell(title.nominalAmount)}
                                            </td>
                                            <td style={{ whiteSpace: "nowrap" }}>
                                                {onSettle ? (
                                                    <button
                                                        type="button"
                                                        className={`btn ${isIncome ? "ent" : "sai"} sm`}
                                                        aria-label={`${isIncome ? "Receber" : "Pagar"} ${title.description}`}
                                                        onClick={() => onSettle(title)}
                                                    >
                                                        {isIncome ? "Receber" : "Pagar"}
                                                    </button>
                                                ) : null}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {titles.isPending ? (
                            <div className="empty">Carregando…</div>
                        ) : titles.data?.items.length === 0 ? (
                            <div className="empty">
                                Nenhum título em aberto nesta posição.
                            </div>
                        ) : null}

                        <Pager
                            page={page}
                            totalPages={titles.data?.totalPages ?? 0}
                            disabled={titles.isFetching}
                            onChange={setPage}
                        />
                    </>
                )}
            </div>

            {settlingHere && companyId ? (
                <FinancialEntrySettlementDialog
                    key={`schedule-settle-${settlingHere.title.id}-${settlingHere.title.version}`}
                    companyId={companyId}
                    target={settlementTargetFromOpenTitle(settlingHere.title)}
                    bankAccounts={bankAccounts.isSuccess ? bankAccounts.data : null}
                    onClose={() => setSettling(null)}
                />
            ) : null}
        </>
    )
}
