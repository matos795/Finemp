import type { ReactNode } from "react"
import { Modal } from "../../../components/finemp/modal"
import {
    formatExactCurrency,
    formatExactDecimalCell,
    formatIsoDate,
    isNegativeExactDecimal,
} from "../../../lib/formatters/decimal"
import type {
    StatementPreviewRow,
    StatementPreviewSession,
} from "../types/statement.types"

interface StatementPreviewDialogProps {
    session: StatementPreviewSession
    accountName: string
    /** Conteúdo extra antes dos botões (confirmações da importação). */
    footer?: ReactNode
    /** Botão principal; ausente = só conferência. */
    action?: ReactNode
    onClose: () => void
    /** Seleção de linhas (confirmação); ausente = sem caixas de seleção. */
    selection?: {
        selected: ReadonlySet<number>
        onToggle: (row: number) => void
    }
    busy?: boolean
}

function rowIsReady(row: StatementPreviewRow) {
    return !row.error && Boolean(row.values)
}

function rowIsSkipped(row: StatementPreviewRow) {
    return !row.error && !row.values
}

/**
 * "Importar extrato – conferência": mesma janela de conferência linha a
 * linha dos layouts de importação do protótipo homologado, com o
 * resultado da prévia do servidor. Nada é gravado aqui (D2).
 */
export function StatementPreviewDialog({
    session,
    accountName,
    footer,
    action,
    onClose,
    selection,
    busy = false,
}: StatementPreviewDialogProps) {
    const { preview } = session
    const ready = preview.rows.filter(rowIsReady)
    const withProblem = preview.rows.filter((row) => Boolean(row.error))
    const skipped = preview.rows.filter(rowIsSkipped)

    const fileFacts = [
        preview.bankCode && `banco ${preview.bankCode}`,
        preview.accountReference && `conta ${preview.accountReference}`,
        preview.currency && `moeda ${preview.currency}`,
    ].filter(Boolean)

    return (
        <Modal
            width={960}
            labelledBy="statement-preview-title"
            onClose={busy ? undefined : onClose}
        >
            <h2 id="statement-preview-title">Importar extrato – conferência</h2>

            <p className="help">
                Arquivo <b>{session.file.name}</b> para a conta{" "}
                <b>{accountName}</b>: {preview.rows.length} linha(s),{" "}
                <b className="pos">{ready.length} pronta(s)</b>
                {skipped.length > 0 && (
                    <>
                        , <b>{skipped.length} ignorada(s)</b>
                    </>
                )}
                {withProblem.length > 0 && (
                    <>
                        , <b className="neg">{withProblem.length} com problema</b>{" "}
                        (não serão importadas)
                    </>
                )}
                . {withProblem.length > 0
                    ? "Corrija as linhas com problema e analise de novo, se precisar."
                    : "As linhas ignoradas não precisam ser corrigidas."}
            </p>

            {(fileFacts.length > 0 || preview.balance?.amount) && (
                <p
                    className="help"
                    style={{ marginTop: 0 }}
                >
                    {fileFacts.length > 0 && <>No arquivo: {fileFacts.join(" · ")}. </>}
                    {preview.balance?.amount && (
                        <>
                            Saldo informado pelo banco:{" "}
                            <b>{formatExactCurrency(preview.balance.amount)}</b>
                            {preview.balance.date && ` em ${formatIsoDate(preview.balance.date)}`}.
                        </>
                    )}
                </p>
            )}

            {preview.warnings.length > 0 && (
                <div className="alert w">
                    <b>Alertas do arquivo:</b>
                    <ul style={{ margin: "6px 0 0", paddingLeft: 18, listStyle: "disc" }}>
                        {preview.warnings.map((warning, index) => (
                            <li key={index}>{warning}</li>
                        ))}
                    </ul>
                </div>
            )}

            <div
                className="tblwrap"
                style={{ maxHeight: "52vh" }}
            >
                <table>
                    <thead>
                        <tr>
                            {selection && <th className="sel-col" />}
                            <th className="n">Linha</th>
                            <th>Data</th>
                            <th>Histórico</th>
                            <th className="n">Valor</th>
                            <th>Situação</th>
                        </tr>
                    </thead>

                    <tbody>
                        {preview.rows.map((row) => {
                            const values = row.values
                            const amount = values?.signedAmount

                            return (
                                <tr key={row.number}>
                                    {selection && (
                                        <td className="sel-col">
                                            <input
                                                type="checkbox"
                                                aria-label={`Importar a linha ${row.number}`}
                                                disabled={!rowIsReady(row) || busy}
                                                checked={selection.selected.has(row.number)}
                                                onChange={() => selection.onToggle(row.number)}
                                            />
                                        </td>
                                    )}

                                    <td className="n">{row.number}</td>

                                    <td style={{ fontSize: 13 }}>
                                        {values ? formatIsoDate(values.postedDate) : row.rawDate}
                                    </td>

                                    <td style={{ fontSize: 13 }}>
                                        {values ? values.memo : row.rawMemo}
                                        {values?.documentReference && (
                                            <div
                                                className="mut"
                                                style={{ fontSize: 12 }}
                                            >
                                                doc {values.documentReference}
                                            </div>
                                        )}
                                    </td>

                                    <td
                                        className={
                                            amount && isNegativeExactDecimal(amount)
                                                ? "n neg"
                                                : "n"
                                        }
                                    >
                                        {amount ? formatExactDecimalCell(amount) : row.rawAmount}
                                    </td>

                                    <td style={{ fontSize: 13 }}>
                                        {row.error ? (
                                            <span className="neg">{row.error}</span>
                                        ) : rowIsSkipped(row) ? (
                                            <>
                                                <span>ignorada</span>
                                                {row.warnings.length > 0 && (
                                                    <span className="mut"> · {row.warnings.join("; ")}</span>
                                                )}
                                            </>
                                        ) : (
                                            <>
                                                <span className="pos">ok</span>
                                                {row.warnings.length > 0 && (
                                                    <span className="mut"> · {row.warnings.join("; ")}</span>
                                                )}
                                            </>
                                        )}
                                    </td>
                                </tr>
                            )
                        })}
                    </tbody>
                </table>
            </div>

            {footer}

            <div className="mact">
                <button
                    type="button"
                    className="btn sec"
                    disabled={busy}
                    onClick={onClose}
                >
                    Cancelar
                </button>

                {action}
            </div>
        </Modal>
    )
}
