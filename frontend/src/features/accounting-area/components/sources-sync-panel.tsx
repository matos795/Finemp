import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiErrorStatus } from "../../../lib/api/api-errors"
import { useAccountingContext } from "../accounting-context"
import { requestSourceSync } from "../api/accounting-area-api"
import { useCompanySyncStatus, useSessionUserId } from "../hooks/use-accounting-area-queries"
import { monthLabel, type CompanySyncSource } from "../lib/accounting-period"
import { accountingQueryKey } from "../lib/query-keys"
import { isRunActive, runChip, syncRequestError } from "../lib/sync-labels"

function brDate(iso: string) {
    const [year, month, day] = iso.slice(0, 10).split("-")

    return `${day}/${month}/${year}`
}

function RequestForm({ source, companyId }: { source: CompanySyncSource; companyId: string }) {
    const userId = useSessionUserId()
    const queryClient = useQueryClient()
    const [startDate, setStartDate] = useState("")
    const [endDate, setEndDate] = useState("")
    const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

    const request = useMutation({
        mutationFn: () => requestSourceSync(source.accountingSourceId, startDate, endDate, crypto.randomUUID()),
        onSuccess: async () => {
            setMessage({ text: "Sincronização solicitada. O estado é atualizado aqui.", error: false })
            await queryClient.invalidateQueries({ queryKey: accountingQueryKey(userId, "sync-status", companyId) })
        },
        onError: (error) => setMessage({ text: syncRequestError(apiErrorStatus(error)), error: true }),
    })

    const valid = startDate !== "" && endDate !== "" && startDate <= endDate
    const blocked = !source.active || isRunActive(source.latestRun)

    return (
        <div className="cf-bar no-print">
            <label>
                De <input type="date" className="search" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
            </label>
            <label>
                Até <input type="date" className="search" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
            </label>
            <button
                type="button"
                className="btn primary sq"
                disabled={!valid || blocked || request.isPending}
                onClick={() => {
                    setMessage(null)
                    request.mutate()
                }}
            >
                {request.isPending ? "Solicitando…" : "Sincronizar período"}
            </button>
            {message && <span className={message.error ? "neg" : "muted"}>{message.text}</span>}
            {!message && blocked && (
                <span className="muted">
                    {source.active ? "Há uma sincronização em andamento." : "Fonte inativa."}
                </span>
            )}
        </div>
    )
}

/**
 * Fontes contábeis da empresa e estado de sincronização (#440), no modal de
 * contexto. Mostra só fatos de SyncRun; não afirma presença do Connector.
 */
export function SourcesSyncPanel() {
    const { company, companyGrant } = useAccountingContext()
    const status = useCompanySyncStatus(company?.companyId ?? null)
    const canSync = companyGrant?.permissions.includes("QUESTOR_SYNC") ?? false

    if (!company) {
        return null
    }

    let body

    if (status.isLoading) {
        body = <div className="empty">Carregando…</div>
    } else if (status.isError) {
        body = (
            <div className="empty">
                {apiErrorStatus(status.error) === 403
                    ? "Sem acesso à sincronização desta empresa (QUESTOR_SYNC)."
                    : "Não foi possível consultar a sincronização."}
            </div>
        )
    } else if (!status.data || status.data.items.length === 0) {
        body = <div className="empty">Nenhuma fonte contábil cadastrada para esta empresa.</div>
    } else {
        body = status.data.items.map((source) => {
            const chip = runChip(source.latestRun)
            const succeeded = source.latestSucceededRun

            return (
                <div key={source.accountingSourceId} className="ctb-src">
                    <div className="ctb-src-h">
                        <b>
                            {source.type === "QUESTOR" ? "Questor" : source.type} ·{" "}
                            {source.connectionMode === "CONNECTOR" ? "FINEMP Connector" : "Leitura direta"}
                        </b>
                        {!source.active && <span className="stc info">Inativa</span>}
                        <span className={`stc ${chip.tone}`}>{chip.label}</span>
                    </div>
                    <div className="sub" style={{ margin: "4px 0 0" }}>
                        {succeeded ? (
                            <>
                                Base sincronizada: <b>{brDate(succeeded.startDate)}</b> a{" "}
                                <b>{brDate(succeeded.endDate)}</b> ({monthLabel(succeeded.endDate)}) ·{" "}
                                {succeeded.importedCount ?? 0} lançamento(s) importado(s)
                            </>
                        ) : (
                            "Nenhuma sincronização concluída."
                        )}
                        {source.latestRun?.status === "FAILED" && (
                            <>
                                {" "}
                                · última execução falhou
                                {source.latestRun.failureCode ? ` (${source.latestRun.failureCode})` : ""}
                            </>
                        )}
                    </div>
                    {canSync && <RequestForm source={source} companyId={company.companyId} />}
                </div>
            )
        })
    }

    return (
        <div className="ctb-srcs">
            <h3 style={{ margin: "0 0 6px" }}>Fontes contábeis</h3>
            {body}
        </div>
    )
}
