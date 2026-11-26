import { useEffect } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { searchMovements, type MovementItem } from "../api/accounting-data-api"
import { useSessionUserId } from "../hooks/use-accounting-area-queries"
import { useOriginCatalogs } from "../hooks/use-origin-catalogs"
import { formatDay } from "../lib/accounting-v2"
import { entryCompositionLines } from "../lib/entry-composition"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { accountingQueryKey } from "../lib/query-keys"
import { MovementMetadataDetail } from "./movement-metadata-detail"

const PAGE_SIZE = 200
/** Até 2.000 linhas do dia na fonte: acima disso a composição é declarada incompleta. */
const MAX_PAGES = 10

function Amount({ value }: { value: string }) {
    return <td className={isNegativeDecimal(value) ? "n neg" : "n"}>{formatExactDecimal(value)}</td>
}

/**
 * Linhas do mesmo lançamento: busca o dia na fonte (o search ordena por
 * data, fonte e lançamento, então as linhas vêm juntas) e fica só com o
 * `sourceEntryKey` escolhido.
 */
async function readEntryLines(companyId: string, movement: MovementItem) {
    const lines: MovementItem[] = []

    for (let page = 0; page < MAX_PAGES; page++) {
        const result = await searchMovements(companyId, {
            accountingSourceId: movement.accountingSourceId,
            startDate: movement.accountingDate,
            endDate: movement.accountingDate,
            page,
            size: PAGE_SIZE,
        })
        const matches = entryCompositionLines(result.items, movement)
        const foundBefore = lines.length > 0
        lines.push(...matches)

        // As linhas de um lançamento vêm contíguas na ordem da API: o bloco acabou quando
        // a página não traz mais nenhuma ou quando a última linha da página já é de outro lançamento.
        const last = result.items[result.items.length - 1]
        const blockEnded =
            (foundBefore && matches.length === 0) ||
            (matches.length > 0 && last !== undefined && entryCompositionLines([last], movement).length === 0)

        if (blockEnded || page + 1 >= result.totalPages) {
            return { lines, complete: true }
        }
    }

    return { lines, complete: false }
}

interface EntryCompositionModalProps {
    companyId: string
    movement: MovementItem
    onClose: () => void
}

/**
 * Composição do lançamento (#mDd do protótipo): todas as linhas do lançamento
 * na origem e os metadados v2 (#509), com a descrição técnica do catálogo de
 * origem ao lado do código cru. Nada é somado nem interpretado.
 */
export function EntryCompositionModal({ companyId, movement, onClose }: EntryCompositionModalProps) {
    const userId = useSessionUserId()
    const entry = useQuery({
        queryKey: accountingQueryKey(
            userId, "movements", companyId, "entry", movement.accountingSourceId, movement.accountingDate,
            movement.sourceEntryKey,
        ),
        queryFn: () => readEntryLines(companyId, movement),
        retry: false,
    })
    const catalogs = useOriginCatalogs(companyId, [movement.accountingSourceId], true)
    const catalog = catalogs.get(movement.accountingSourceId)
    const lines = entry.data?.lines ?? []
    const metadataSource = lines.find((line) => line.sourceMetadataStatus === "SYNCHRONIZED") ?? movement

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") onClose()
        }

        window.addEventListener("keydown", onKey)

        return () => window.removeEventListener("keydown", onKey)
    }, [onClose])

    return (
        <div
            className="modal"
            id="mDd"
            role="dialog"
            aria-modal="true"
            aria-labelledby="mDdEntryTit"
            onClick={(event) => {
                if (event.target === event.currentTarget) onClose()
            }}
        >
            <div className="dlg vr-dlg">
                <div className="secthead">
                    <h2 id="mDdEntryTit">Composição do lançamento</h2>
                    <button type="button" className="btn sq" onClick={onClose}>
                        Fechar
                    </button>
                </div>

                <div className="sub">
                    Lançamento <b className="mono">{movement.sourceEntryKey}</b> de{" "}
                    <span className="mono">{formatDay(movement.accountingDate)}</span>
                </div>

                {entry.isLoading ? (
                    <div className="empty">Carregando…</div>
                ) : entry.isError ? (
                    <div className="empty">
                        {apiErrorStatus(entry.error) === 403
                            ? "Sem acesso aos lançamentos contábeis desta empresa."
                            : "Não foi possível carregar a composição do lançamento."}{" "}
                        <button type="button" className="lnk" onClick={() => void entry.refetch()}>
                            Tentar de novo
                        </button>
                    </div>
                ) : (
                    <>
                        <div className="tbl-wrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Conta</th>
                                        <th>Histórico</th>
                                        <th className="r">Débito</th>
                                        <th className="r">Crédito</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {lines.map((line) => (
                                        <tr key={line.id}>
                                            <td className="wrapcell">
                                                {line.accountDescription ?? ""}
                                                <small className="dp-cod">{line.accountKey}</small>
                                            </td>
                                            <td className="wrapcell">{line.history ?? ""}</td>
                                            {line.side === "DEBIT" ? <Amount value={line.amount} /> : <td className="n" />}
                                            {line.side === "CREDIT" ? <Amount value={line.amount} /> : <td className="n" />}
                                        </tr>
                                    ))}
                                    {lines.length === 0 && (
                                        <tr>
                                            <td colSpan={4} className="empty">
                                                Nenhuma linha deste lançamento foi encontrada no dia.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                        {entry.data && !entry.data.complete && (
                            <div className="warnbar" role="status" style={{ marginTop: 12 }}>
                                O dia tem mais de {PAGE_SIZE * MAX_PAGES} linhas nesta fonte: a composição pode estar
                                incompleta.
                            </div>
                        )}
                    </>
                )}

                <h3 style={{ marginTop: 16 }}>Origem técnica</h3>
                <MovementMetadataDetail item={metadataSource} originDescriptions={catalog} />
            </div>
        </div>
    )
}
