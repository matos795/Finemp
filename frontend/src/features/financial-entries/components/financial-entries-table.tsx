import {
    formatExactDecimal,
    formatIsoDate,
} from "../../../lib/formatters/decimal"
import { formatCompetence } from "../../reports/lib/calendar-labels"
import type { FinancialEntry } from "../types/financial-entry.types"
import { FinancialEntryStatusBadge } from "./financial-entry-status-badge"

const PENDING_TITLE = "Aguardando dado da API"

interface FinancialEntriesTableProps {
    entries: FinancialEntry[]
    canManage: boolean
    onEdit: (entry: FinancialEntry) => void
}

/**
 * Tabela do protótipo (renderLanc). Ordem, valor efetivo, situação e
 * rótulos vêm do servidor; aqui só há formatação.
 */
export function FinancialEntriesTable({
    entries,
    canManage,
    onEdit,
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
                                    <button
                                        type="button"
                                        className="btn sec sm"
                                        onClick={() => onEdit(entry)}
                                    >
                                        Editar
                                    </button>
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
