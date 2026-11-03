import {
    formatExactDecimalCell,
    formatIsoDate,
    isNegativeExactDecimal,
} from "../../../lib/formatters/decimal"
import type { StatementItem } from "../types/statement.types"
import { StatementCandidatesCells } from "./statement-candidates-cell"
import { IgnoreStatementButton } from "./ignore-statement-button"

interface StatementItemsTableProps {
    companyId: string | undefined
    /** FINANCIAL_ENTRY_MANAGE (só UX; o backend autoriza). */
    canIgnore: boolean
    items: StatementItem[]
    loading: boolean
    dimmed: boolean
}

/**
 * Tabela de movimentos do extrato (ext do protótipo homologado).
 * Pendentes mostram as sugestões do servidor. Conciliar, criar
 * lançamento e desfazer dependem de contratos ainda inexistentes na API
 * (PA1) e ficam inativos.
 */
export function StatementItemsTable({
    companyId,
    canIgnore,
    items,
    loading,
    dimmed,
}: StatementItemsTableProps) {
    return (
        <div className="tblwrap">
            <table style={dimmed ? { opacity: 0.6 } : undefined}>
                <thead>
                    <tr>
                        <th>Data</th>
                        <th>Histórico do banco</th>
                        <th className="n">Valor</th>
                        <th>Lançamento no sistema</th>
                        <th>Ações</th>
                    </tr>
                </thead>

                <tbody>
                    {loading && (
                        <tr>
                            <td
                                colSpan={5}
                                className="empty"
                            >
                                Carregando…
                            </td>
                        </tr>
                    )}

                    {items.map((item) => {
                        const amount = item.values.signedAmount

                        return (
                            <tr key={item.id}>
                                <td>{formatIsoDate(item.values.postedDate)}</td>

                                <td style={{ fontSize: 13 }}>
                                    {item.values.memo}
                                    {item.values.documentReference && (
                                        <div
                                            className="mut"
                                            style={{ fontSize: 12 }}
                                        >
                                            doc {item.values.documentReference}
                                        </div>
                                    )}
                                </td>

                                <td className={isNegativeExactDecimal(amount) ? "n neg" : "n pos"}>
                                    {formatExactDecimalCell(amount)}
                                </td>

                                {item.status === "PENDING" ? (
                                    <StatementCandidatesCells
                                        companyId={companyId}
                                        statementId={item.id}
                                        actions={
                                            <>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    disabled
                                                    title="Em breve"
                                                >
                                                    Criar lançamento
                                                </button>{" "}
                                                <IgnoreStatementButton
                                                    companyId={companyId}
                                                    item={item}
                                                    canIgnore={canIgnore}
                                                />
                                            </>
                                        }
                                    />
                                ) : (
                                    <>
                                        <td>
                                            {item.status === "RECONCILED" ? (
                                                <span className="badge b-ok">conciliado</span>
                                            ) : (
                                                <span className="badge b-neu">ignorado</span>
                                            )}
                                        </td>

                                        <td style={{ whiteSpace: "nowrap" }}>
                                            <button
                                                type="button"
                                                className="btn sec sm"
                                                disabled
                                                title={
                                                    item.status === "RECONCILED"
                                                        ? "Desconciliar ainda não existe na API"
                                                        : "Voltar para pendente ainda não existe na API"
                                                }
                                            >
                                                {item.status === "RECONCILED"
                                                    ? "Desfazer"
                                                    : "Voltar para pendente"}
                                            </button>
                                        </td>
                                    </>
                                )}
                            </tr>
                        )
                    })}
                </tbody>
            </table>
        </div>
    )
}
