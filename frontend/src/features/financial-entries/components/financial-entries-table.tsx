import {
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { formatCompetence } from "../../reports/lib/calendar-labels"
import type { FinancialEntry } from "../types/financial-entry.types"
import { settlementCopy } from "../lib/financial-entry-settlement"
import { FinancialEntryStatusBadge } from "./financial-entry-status-badge"

const PENDING_TITLE = "Aguardando dado da API"

interface FinancialEntriesTableProps {
    entries: FinancialEntry[]
    /** Mostra a coluna Ações (FINANCIAL_ENTRY_MANAGE). */
    canManage: boolean
    /** "Editar" depende dos catálogos do formulário estarem prontos. */
    canEdit: boolean
    /** "Pagar"/"Receber" por linha (situação do backend + permissão). */
    canSettle: (entry: FinancialEntry) => boolean
    /** "Desfazer baixa" por linha (só título pago, decisão de UX). */
    canReverse: (entry: FinancialEntry) => boolean
    /** Id do lançamento cujo estorno está em andamento. */
    reversingId: string | null
    onEdit: (entry: FinancialEntry) => void
    onSettle: (entry: FinancialEntry) => void
    onReverse: (entry: FinancialEntry) => void
}

/**
 * Tabela do protótipo (renderLanc). Ordem, valor efetivo, situação e
 * rótulos vêm do servidor; aqui só há formatação.
 */
export function FinancialEntriesTable({
    entries,
    canManage,
    canEdit,
    canSettle,
    canReverse,
    reversingId,
    onEdit,
    onSettle,
    onReverse,
}: FinancialEntriesTableProps) {
    return (
        <table>
            <thead>
                <tr>
                    <th>Data</th>
                    <th>Descrição</th>
                    <th>Categoria</th>
                    <th>Conta</th>
                    <th className="n">Valor</th>
                    <th>Situação</th>
                    {canManage ? <th>Ações</th> : null}
                </tr>
            </thead>

            <tbody>
                {entries.map((entry) => {
                    const values = entry.values
                    const isIncome = values.direction === "INCOME"
                    const details = [
                        entry.labels.partnerName,
                        values.documentNumber,
                        values.paymentMethod,
                    ].filter(Boolean)

                    return (
                        <tr key={entry.id}>
                            <td style={{ whiteSpace: "nowrap" }}>
                                {values.paymentDate ? (
                                    <>
                                        {formatIsoDate(values.paymentDate)}
                                        <div className="mut" style={{ fontSize: 12 }}>
                                            venc. {formatIsoDate(values.dueDate)}
                                        </div>
                                    </>
                                ) : (
                                    <>
                                        {formatIsoDate(values.dueDate)}
                                        <div className="mut" style={{ fontSize: 12 }}>vencimento</div>
                                    </>
                                )}
                                <div className="mut" style={{ fontSize: 11 }}>
                                    doc. {formatIsoDate(values.documentDate)}
                                </div>
                            </td>

                            <td>
                                {values.description}
                                {entry.series ? (
                                    <>
                                        {" "}
                                        <span className="badge b-neu">
                                            {entry.series.number}/{entry.series.quantity}
                                        </span>
                                    </>
                                ) : null}
                                {details.length > 0 ? (
                                    <div className="mut" style={{ fontSize: 12 }}>
                                        {details.join(" · ")}
                                    </div>
                                ) : null}
                            </td>

                            <td style={{ fontSize: 13 }}>
                                {entry.labels.categoryName ?? "—"}
                                <div className="mut" style={{ fontSize: 12 }}>
                                    {[
                                        entry.labels.categoryCode,
                                        entry.effectiveCostBehavior === "FIXED" ? "fixo" : "variável",
                                        `comp. ${formatCompetence(values.competence)}`,
                                        entry.labels.centerName,
                                    ].filter(Boolean).join(" · ")}
                                </div>
                            </td>

                            <td style={{ fontSize: 13 }}>{entry.labels.bankName ?? "—"}</td>

                            <td className={isIncome ? "n pos" : "n neg"}>
                                {isIncome ? "+" : "-"} {formatExactDecimal(entry.effectiveAmount)}
                            </td>

                            <td>
                                <FinancialEntryStatusBadge
                                    status={entry.status}
                                    direction={values.direction}
                                />
                            </td>

                            {canManage ? (
                                <td style={{ whiteSpace: "nowrap" }}>
                                    {canSettle(entry) ? (
                                        <>
                                            <button
                                                type="button"
                                                className={isIncome ? "btn ent sm" : "btn sai sm"}
                                                onClick={() => onSettle(entry)}
                                            >
                                                {settlementCopy(values.direction).action}
                                            </button>{" "}
                                        </>
                                    ) : null}
                                    {canReverse(entry) ? (
                                        <>
                                            <button
                                                type="button"
                                                className="btn sec sm"
                                                title="Voltar para em aberto"
                                                disabled={reversingId !== null}
                                                onClick={() => onReverse(entry)}
                                            >
                                                {reversingId === entry.id ? "Desfazendo…" : "Desfazer baixa"}
                                            </button>{" "}
                                        </>
                                    ) : null}
                                    {canEdit ? (
                                        <button
                                            type="button"
                                            className="btn sec sm"
                                            onClick={() => onEdit(entry)}
                                        >
                                            Editar
                                        </button>
                                    ) : null}
                                </td>
                            ) : null}
                        </tr>
                    )
                })}
            </tbody>

            <tfoot>
                <tr>
                    <td colSpan={4}>Totais do filtro</td>
                    <td
                        className="n"
                        colSpan={canManage ? 3 : 2}
                        style={{ textAlign: "left" }}
                        title={PENDING_TITLE}
                    >
                        —
                    </td>
                </tr>
            </tfoot>
        </table>
    )
}
