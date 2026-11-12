import { useEffect, useState } from "react"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    closeFinancialPeriod,
    listFinancialPeriodHistory,
    listFinancialPeriods,
    reopenFinancialPeriod,
    type FinancialPeriodHistory,
    type FinancialPeriodState,
    type PagedResponse,
} from "../api/financial-period-api"

const PAGE_SIZE = 20

function errorMessage(error: unknown) {
    if (error instanceof ApiError && error.status === 409) {
        return "O período mudou desde a última leitura. Atualize e tente novamente."
    }

    if (error instanceof ApiError && error.status === 403) {
        return "Seu usuário não possui permissão para esta operação de período."
    }

    return "Não foi possível concluir a operação de período."
}

export function FinancialPeriodsPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id ?? ""
    const canClose = hasPermission("FINANCIAL_PERIOD_CLOSE")
    const canReopen = hasPermission("FINANCIAL_PERIOD_REOPEN")

    const [result, setResult] = useState<PagedResponse<FinancialPeriodState> | null>(null)
    const [page, setPage] = useState(0)
    const [period, setPeriod] = useState("")
    const [selectedPeriod, setSelectedPeriod] = useState("")
    const [reason, setReason] = useState("")
    const [history, setHistory] = useState<FinancialPeriodHistory[]>([])
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState("")
    const [message, setMessage] = useState("")

    const selected =
        result?.items.find((item) => item.period === selectedPeriod) ?? null

    async function load(targetPage = page) {
        if (!companyId) {
            setResult(null)
            return
        }

        setBusy(true)
        setError("")

        try {
            const next = await listFinancialPeriods(companyId, targetPage, PAGE_SIZE)
            setResult(next)
            setSelectedPeriod((current) =>
                next.items.some((item) => item.period === current)
                    ? current
                    : next.items[0]?.period ?? "",
            )
        } catch (caught) {
            setError(errorMessage(caught))
            setResult(null)
        } finally {
            setBusy(false)
        }
    }

    async function loadHistory(targetPeriod: string) {
        if (!companyId || !targetPeriod) {
            setHistory([])
            return
        }

        try {
            const next = await listFinancialPeriodHistory(
                companyId,
                targetPeriod,
                0,
                20,
            )
            setHistory(next.items)
        } catch {
            setHistory([])
        }
    }

    useEffect(() => {
        setPage(0)
        setPeriod("")
        setSelectedPeriod("")
        setReason("")
        setHistory([])
        void load(0)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [companyId])

    useEffect(() => {
        void loadHistory(selectedPeriod)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedPeriod])

    async function close() {
        if (!companyId || !period || !canClose) return

        const current =
            result?.items.find((item) => item.period === period) ?? null

        setBusy(true)
        setError("")
        setMessage("")

        try {
            const receipt = await closeFinancialPeriod(
                companyId,
                period,
                current?.version ?? null,
            )
            setMessage(`Período ${receipt.period} fechado.`)
            setSelectedPeriod(receipt.period)
            await load(page)
            await loadHistory(receipt.period)
        } catch (caught) {
            setError(errorMessage(caught))
        } finally {
            setBusy(false)
        }
    }

    async function reopen() {
        if (
            !companyId ||
            !selected ||
            selected.version === null ||
            !selected.closed ||
            !canReopen ||
            !reason.trim()
        ) {
            return
        }

        setBusy(true)
        setError("")
        setMessage("")

        try {
            const receipt = await reopenFinancialPeriod(
                companyId,
                selected.period,
                selected.version,
                reason,
            )
            setMessage(`Período ${receipt.period} reaberto.`)
            setReason("")
            await load(page)
            await loadHistory(receipt.period)
        } catch (caught) {
            setError(errorMessage(caught))
        } finally {
            setBusy(false)
        }
    }

    if (!selectedCompany) {
        return <div className="mbox">Selecione uma empresa para consultar períodos financeiros.</div>
    }

    return (
        <div>
            <section className="mbox">
                <h1>Fechamento de períodos</h1>
                <p className="help">
                    Fechamento e reabertura são comandos financeiros formais.
                    Versão, idempotência e invariantes são validadas pelo backend.
                </p>
                {message && <div className="alert i">{message}</div>}
                {error && <div className="alert e">{error}</div>}
            </section>

            {canClose && (
                <section className="mbox">
                    <h2>Fechar competência</h2>
                    <div className="fl">
                        <label htmlFor="financial-period">Competência</label>
                        <input
                            id="financial-period"
                            type="month"
                            value={period}
                            onChange={(event) => setPeriod(event.target.value)}
                        />
                    </div>
                    <button
                        className="btn"
                        type="button"
                        disabled={busy || !period}
                        onClick={() => void close()}
                    >
                        Fechar período
                    </button>
                </section>
            )}

            <section className="mbox">
                <h2>Períodos registrados</h2>
                {busy && !result && <p>Carregando...</p>}
                {!busy && result?.items.length === 0 && (
                    <p className="help">
                        Nenhum fechamento foi registrado. Um período aberto só
                        passa a ter estado persistido quando participa de um
                        comando formal.
                    </p>
                )}

                <div style={{ display: "grid", gap: 8 }}>
                    {result?.items.map((item) => (
                        <button
                            key={item.period}
                            className="card"
                            type="button"
                            aria-pressed={selectedPeriod === item.period}
                            onClick={() => setSelectedPeriod(item.period)}
                            style={{ textAlign: "left", width: "100%", cursor: "pointer" }}
                        >
                            <strong>{item.period}</strong>
                            <div>{item.closed ? "Fechado" : "Aberto"}</div>
                            <small>Versão {item.version ?? 0}</small>
                        </button>
                    ))}
                </div>

                {result && result.totalPages > 1 && (
                    <div style={{ display: "flex", justifyContent: "space-between", marginTop: 12 }}>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={busy || result.page === 0}
                            onClick={() => {
                                const next = Math.max(0, page - 1)
                                setPage(next)
                                void load(next)
                            }}
                        >
                            Anterior
                        </button>
                        <small>Página {result.page + 1} de {result.totalPages}</small>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={busy || result.page + 1 >= result.totalPages}
                            onClick={() => {
                                const next = page + 1
                                setPage(next)
                                void load(next)
                            }}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>

            {selected && (
                <section className="mbox">
                    <h2>Competência {selected.period}</h2>
                    <p>
                        Estado: <strong>{selected.closed ? "Fechado" : "Aberto"}</strong>
                    </p>

                    {selected.closed && canReopen && (
                        <div>
                            <div className="fl">
                                <label htmlFor="reopen-reason">Motivo da reabertura</label>
                                <textarea
                                    id="reopen-reason"
                                    value={reason}
                                    onChange={(event) => setReason(event.target.value)}
                                    rows={3}
                                />
                            </div>
                            <button
                                className="btn"
                                type="button"
                                disabled={busy || !reason.trim()}
                                onClick={() => void reopen()}
                            >
                                Reabrir período
                            </button>
                        </div>
                    )}

                    <h3>Histórico recente</h3>
                    {history.length === 0 ? (
                        <p className="help">Nenhum evento disponível.</p>
                    ) : (
                        <div style={{ display: "grid", gap: 8 }}>
                            {history.map((event) => (
                                <div className="card" key={event.id}>
                                    <strong>{event.action}</strong>
                                    <div>{new Date(event.occurredAt).toLocaleString("pt-BR")}</div>
                                    {event.reason && <small>Motivo: {event.reason}</small>}
                                </div>
                            ))}
                        </div>
                    )}
                </section>
            )}
        </div>
    )
}
