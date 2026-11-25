import { Fragment, useEffect, useState } from "react"
import { useInfiniteQuery } from "@tanstack/react-query"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { searchMovements, type ImportedAccount } from "../api/accounting-data-api"
import { useCompanySyncStatus, useSessionUserId } from "../hooks/use-accounting-area-queries"
import { accountingQueryKey } from "../lib/query-keys"
import { lastDayOf, monthWindow, syncedUntil } from "../lib/accounting-period"
import { formatExactDecimal, isNegativeDecimal } from "../lib/exact-decimal"
import { MovementMetadataDetail } from "./movement-metadata-detail"

const PAGE_SIZE = 100

/** Rótulos de notSynchronized (#444): o que o contrato canônico ainda não carrega. */
const NOT_SYNCHRONIZED_LABELS: Record<string, string> = {
    COUNTERPART: "contrapartida",
    DOCUMENT: "documento",
    COST_CENTER: "centro de custo",
    CLOSING_ENTRY: "lançamento de encerramento",
    OPENING_BALANCE: "saldo inicial",
}

function brDate(iso: string) {
    const [year, month, day] = iso.split("-")

    return `${day}/${month}/${year}`
}

function Amount({ value }: { value: string }) {
    return <td className={isNegativeDecimal(value) ? "n neg" : "n"}>{formatExactDecimal(value)}</td>
}

interface MovementDrilldownModalProps {
    companyId: string
    account: ImportedAccount
    onClose: () => void
}

/**
 * Composição (#mDd do protótipo) de uma conta: lançamentos do período, com
 * débitos, créditos e totais exatos vindos do backend (#444). Contrapartida e
 * demais campos não sincronizados não são exibidos como existentes.
 */
export function MovementDrilldownModal({ companyId, account, onClose }: MovementDrilldownModalProps) {
    const userId = useSessionUserId()
    const sync = useCompanySyncStatus(companyId)
    const base = syncedUntil(sync.data)
    const today = new Date()
    const fallbackMonth = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`
    const initial = monthWindow((base ?? fallbackMonth).slice(0, 7), 12)
    const [range, setRange] = useState<{ from: string; to: string } | null>(null)
    const [open, setOpen] = useState<string | null>(null)
    const from = range?.from ?? initial.startDate.slice(0, 7)
    const to = range?.to ?? initial.endDate.slice(0, 7)
    const startDate = `${from}-01`
    const endDate = lastDayOf(to)
    const validRange = startDate <= endDate

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") onClose()
        }

        window.addEventListener("keydown", onKey)

        return () => window.removeEventListener("keydown", onKey)
    }, [onClose])

    const movements = useInfiniteQuery({
        queryKey: accountingQueryKey(userId, "movements", companyId, account.accountId, startDate, endDate),
        queryFn: ({ pageParam }) =>
            searchMovements(companyId, { accountId: account.accountId, startDate, endDate, page: pageParam, size: PAGE_SIZE }),
        initialPageParam: 0,
        getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
        enabled: validRange && !sync.isLoading,
        retry: false,
    })

    const firstPage = movements.data?.pages[0]
    const rows = movements.data?.pages.flatMap((page) => page.items) ?? []
    const remaining = firstPage ? firstPage.totalItems - rows.length : 0
    const missing = (firstPage?.notSynchronized ?? [])
        .map((code) => NOT_SYNCHRONIZED_LABELS[code] ?? code)
        .join(", ")

    return (
        <div
            className="modal"
            id="mDd"
            role="dialog"
            aria-modal="true"
            aria-labelledby="mDdTit"
            onClick={(event) => {
                if (event.target === event.currentTarget) onClose()
            }}
        >
            <div className="dlg vr-dlg">
                <div className="secthead">
                    <h2 id="mDdTit">Composição</h2>
                    <button type="button" className="btn sq" onClick={onClose}>
                        Fechar
                    </button>
                </div>

                <div className="sub">
                    <b>{account.description}</b> <span className="mono">{account.accountKey}</span>
                </div>

                <div className="cf-bar no-print">
                    <label>
                        De{" "}
                        <input
                            type="month"
                            className="search"
                            value={from}
                            onChange={(event) => event.target.value && setRange({ from: event.target.value, to })}
                        />
                    </label>
                    <label>
                        Até{" "}
                        <input
                            type="month"
                            className="search"
                            value={to}
                            onChange={(event) => event.target.value && setRange({ from, to: event.target.value })}
                        />
                    </label>
                    {!validRange && <span className="muted">O início deve ser anterior ao fim.</span>}
                </div>

                {movements.isLoading ? (
                    <div className="empty">Carregando…</div>
                ) : movements.isError ? (
                    <div className="empty">
                        {apiErrorStatus(movements.error) === 403
                            ? "Sem acesso aos lançamentos desta empresa."
                            : "Não foi possível carregar os lançamentos."}
                    </div>
                ) : (
                    <div className="tbl-wrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Data</th>
                                    <th>Lançamento</th>
                                    <th>Histórico</th>
                                    <th className="r">Débito</th>
                                    <th className="r">Crédito</th>
                                </tr>
                            </thead>
                            <tbody>
                                {rows.map((item) => (
                                    <Fragment key={item.id}>
                                        <tr>
                                            <td className="mono">{brDate(item.accountingDate)}</td>
                                            <td className="mono">
                                                <button
                                                    type="button"
                                                    className="lnk"
                                                    aria-expanded={open === item.id}
                                                    title="Metadados da origem"
                                                    onClick={() => setOpen(open === item.id ? null : item.id)}
                                                >
                                                    {item.sourceEntryKey}
                                                </button>
                                            </td>
                                            <td className="wrapcell">{item.history ?? ""}</td>
                                            {item.side === "DEBIT" ? <Amount value={item.amount} /> : <td className="n" />}
                                            {item.side === "CREDIT" ? <Amount value={item.amount} /> : <td className="n" />}
                                        </tr>
                                        {open === item.id && (
                                            <tr className="dp-ed">
                                                <td colSpan={5}>
                                                    <MovementMetadataDetail item={item} />
                                                </td>
                                            </tr>
                                        )}
                                    </Fragment>
                                ))}
                                {rows.length === 0 && (
                                    <tr>
                                        <td colSpan={5} className="empty">
                                            Sem lançamentos no período.
                                        </td>
                                    </tr>
                                )}
                                {remaining > 0 && (
                                    <tr>
                                        <td colSpan={5}>
                                            <button
                                                type="button"
                                                className="lnk"
                                                disabled={movements.isFetchingNextPage}
                                                onClick={() => void movements.fetchNextPage()}
                                            >
                                                {movements.isFetchingNextPage
                                                    ? "Carregando…"
                                                    : `Mostrar mais ${Math.min(PAGE_SIZE, remaining)} de ${remaining} lançamentos restantes`}
                                            </button>
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                            {firstPage && (
                                <tfoot>
                                    <tr className="tot">
                                        <td colSpan={3}>Totais do período ({firstPage.totalItems} lançamentos)</td>
                                        <Amount value={firstPage.totals.debit} />
                                        <Amount value={firstPage.totals.credit} />
                                    </tr>
                                </tfoot>
                            )}
                        </table>
                    </div>
                )}

                {missing && (
                    <p className="sub" style={{ marginTop: 10 }}>
                        Ainda não sincronizados do Questor: {missing}. Por isso o resumo por contrapartida do
                        protótipo não é exibido.
                    </p>
                )}
            </div>
        </div>
    )
}
