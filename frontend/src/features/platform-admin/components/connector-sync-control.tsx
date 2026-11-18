import { useEffect, useMemo, useState } from "react"
import { ApiError } from "../../../lib/api/api-client"
import {
    listSyncRuns,
    requestInitialSyncRun,
    type AccountingSource,
    type SyncRun,
} from "../api/platform-admin-api"

function describeSyncError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 403) {
            return "Seu acesso precisa de QUESTOR_SYNC para sincronizar esta empresa."
        }
        if (error.status === 409) {
            return "Já existe uma sincronização ativa para esta fonte."
        }
        if (error.status === 400) {
            return "Confira o período informado."
        }
    }

    return "Não foi possível controlar a sincronização do Connector."
}

function statusLabel(status: SyncRun["status"]) {
    switch (status) {
        case "REQUESTED": return "Solicitada"
        case "RUNNING": return "Em execução"
        case "SUCCEEDED": return "Concluída"
        case "FAILED": return "Falhou"
        case "CANCELLED": return "Cancelada"
    }
}

function statusClass(status: SyncRun["status"]) {
    if (status === "SUCCEEDED") return "badge b-ok"
    if (status === "FAILED" || status === "CANCELLED") return "badge b-neu"
    return "badge"
}

export function ConnectorSyncControl({
    source,
    disabled = false,
}: {
    source: AccountingSource
    disabled?: boolean
}) {
    const [runs, setRuns] = useState<SyncRun[]>([])
    const [startDate, setStartDate] = useState("")
    const [endDate, setEndDate] = useState("")
    const [busy, setBusy] = useState(false)
    const [message, setMessage] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)

    const activeRun = useMemo(
        () => runs.some((run) => run.status === "REQUESTED" || run.status === "RUNNING"),
        [runs],
    )

    const canRequest =
        source.active &&
        source.connectionMode === "CONNECTOR" &&
        !!startDate &&
        !!endDate &&
        startDate <= endDate &&
        !activeRun

    async function refresh() {
        const page = await listSyncRuns(source.id)
        setRuns(page.items)
    }

    useEffect(() => {
        let active = true

        void listSyncRuns(source.id)
            .then((page) => {
                if (active) setRuns(page.items)
            })
            .catch((caught) => {
                if (active) setError(describeSyncError(caught))
            })

        return () => {
            active = false
        }
    }, [source.id])

    async function run(action: () => Promise<void>) {
        setBusy(true)
        setMessage(null)
        setError(null)

        try {
            await action()
        } catch (caught) {
            setError(describeSyncError(caught))
        } finally {
            setBusy(false)
        }
    }

    return (
        <div className="mbox" style={{ marginTop: 16 }}>
            <h3>Sincronização Questor</h3>
            <p className="help">
                Solicita ao Connector uma leitura somente de consulta no Questor.
                Nesta fase, apenas o modo inicial está suportado.
            </p>

            {message ? <div className="alert i">{message}</div> : null}
            {error ? <div className="alert e">{error}</div> : null}

            <div
                style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
                    gap: 10,
                }}
            >
                <div className="fl">
                    <label htmlFor="connector-sync-start">Data inicial</label>
                    <input
                        id="connector-sync-start"
                        type="date"
                        value={startDate}
                        disabled={disabled || busy}
                        onChange={(event) => setStartDate(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="connector-sync-end">Data final</label>
                    <input
                        id="connector-sync-end"
                        type="date"
                        value={endDate}
                        disabled={disabled || busy}
                        onChange={(event) => setEndDate(event.target.value)}
                    />
                </div>
            </div>

            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button
                    type="button"
                    className="btn"
                    disabled={disabled || busy || !canRequest}
                    onClick={() => void run(async () => {
                        await requestInitialSyncRun(
                            source.id,
                            crypto.randomUUID(),
                            startDate,
                            endDate,
                        )
                        await refresh()
                        setMessage("Sincronização solicitada. O Connector poderá assumir o trabalho no próximo ciclo.")
                    })}
                >
                    Sincronizar agora
                </button>

                <button
                    type="button"
                    className="btn sec"
                    disabled={disabled || busy}
                    onClick={() => void run(async () => {
                        await refresh()
                        setMessage("Status atualizado.")
                    })}
                >
                    Atualizar status
                </button>
            </div>

            <div className="tblwrap" style={{ marginTop: 12 }}>
                <table>
                    <thead>
                        <tr>
                            <th>Solicitada em</th>
                            <th>Período</th>
                            <th>Status</th>
                            <th>Lidos</th>
                            <th>Importados</th>
                            <th>Ignorados</th>
                        </tr>
                    </thead>
                    <tbody>
                        {runs.map((syncRun) => (
                            <tr key={syncRun.id}>
                                <td>{new Date(syncRun.requestedAt).toLocaleString("pt-BR")}</td>
                                <td>{syncRun.startDate} a {syncRun.endDate}</td>
                                <td>
                                    <span className={statusClass(syncRun.status)}>
                                        {statusLabel(syncRun.status)}
                                    </span>
                                    {syncRun.failureCode ? (
                                        <div className="help">{syncRun.failureCode}</div>
                                    ) : null}
                                </td>
                                <td>{syncRun.readCount ?? "—"}</td>
                                <td>{syncRun.importedCount ?? "—"}</td>
                                <td>{syncRun.skippedCount ?? "—"}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {runs.length === 0 ? (
                <div className="empty">Nenhuma sincronização registrada para esta fonte.</div>
            ) : null}
        </div>
    )
}
