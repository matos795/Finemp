import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import {
    listPlatformAuditEvents,
    type AuditEvent,
} from "../api/audit-api"

const PAGE_SIZE = 50

const actionLabels: Record<string, string> = {
    INVITATION_ISSUED: "Convite emitido",
    ACCOUNT_ACTIVATED: "Conta ativada",
    PASSWORD_RESET_ISSUED: "Redefinição de senha solicitada",
    PASSWORD_CHANGED: "Senha alterada",
    USER_DEACTIVATED: "Usuário desativado",
    USER_REACTIVATED: "Usuário reativado",
    PLATFORM_PERMISSIONS_REPLACED: "Permissões de plataforma alteradas",
    FIRM_ACCESS_GRANTED: "Acesso ao escritório concedido",
    FIRM_ACCESS_REPLACED: "Acesso ao escritório alterado",
    FIRM_ACCESS_REVOKED: "Acesso ao escritório revogado",
    COMPANY_ACCESS_REPLACED: "Acesso à Company alterado",
    COMPANY_ACCESS_REVOKED: "Acesso à Company revogado",
    CLIENT_DELETED: "Cliente excluído",
    ACCOUNTING_FIRM_DELETED: "Escritório excluído",
    FINANCIAL_PERIOD_CLOSED: "Período financeiro fechado",
    FINANCIAL_PERIOD_REOPENED: "Período financeiro reaberto",
    FINANCIAL_WITHHOLDING_CREATED: "Retenção criada",
    FINANCIAL_WITHHOLDING_UPDATED: "Retenção alterada",
    FINANCIAL_WITHHOLDING_DELETED: "Retenção excluída",
    FINANCIAL_STATEMENT_IMPORTED: "Extrato importado",
    FINANCIAL_STATEMENT_IGNORED: "Movimento de extrato ignorado",
    FINANCIAL_STATEMENT_RECONCILED: "Movimento de extrato conciliado",
}

function auditActionLabel(action: string) {
    return actionLabels[action] ?? action
}

export function AuditEventCard({ event }: { event: AuditEvent }) {
    return (
        <article className="card">
            <strong>{auditActionLabel(event.action)}</strong>
            <div>{new Date(event.occurredAt).toLocaleString("pt-BR")}</div>
            <small>
                Ator: {event.actorUserAccountId ?? "sistema"} · Alvo:{" "}
                {event.subjectUserAccountId}
            </small>
            {(event.resourceType || event.resourceId) && (
                <small>
                    Recurso: {event.resourceType ?? "—"} ·{" "}
                    {event.resourceId ?? "—"}
                </small>
            )}
            {event.companyId && <small>Company: {event.companyId}</small>}
        </article>
    )
}

export function PlatformAuditPage() {
    const [page, setPage] = useState(0)

    const query = useQuery({
        queryKey: ["platform-audit-events", page] as const,
        queryFn: () => listPlatformAuditEvents(page, PAGE_SIZE),
        retry: false,
    })

    const error =
        query.error instanceof ApiError && query.error.status === 403
            ? "A consulta do journal global exige SYSTEM_ADMIN."
            : query.error
              ? "Não foi possível carregar o registro de atividades."
              : ""

    return (
        <div>
            <section className="mbox">
                <h1>Registro de atividades</h1>
                <p className="help">
                    Journal administrativo global. Esta primeira visão mostra
                    apenas metadados de rastreabilidade e não expõe estados
                    before/after, motivos livres ou snapshots de permissões.
                </p>
                {error && <div className="alert e">{error}</div>}
            </section>

            <section className="mbox">
                {query.isLoading && <p>Carregando...</p>}
                {!query.isLoading && query.data?.items.length === 0 && (
                    <p className="help">Nenhum evento auditado.</p>
                )}

                <div style={{ display: "grid", gap: 8 }}>
                    {query.data?.items.map((event) => (
                        <AuditEventCard key={event.id} event={event} />
                    ))}
                </div>

                {query.data && query.data.totalPages > 1 && (
                    <div style={{ display: "flex", justifyContent: "space-between", marginTop: 12 }}>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={query.isFetching || query.data.page === 0}
                            onClick={() => setPage((current) => Math.max(0, current - 1))}
                        >
                            Anterior
                        </button>
                        <small>
                            Página {query.data.page + 1} de {query.data.totalPages}
                        </small>
                        <button
                            className="btn sec"
                            type="button"
                            disabled={
                                query.isFetching ||
                                query.data.page + 1 >= query.data.totalPages
                            }
                            onClick={() => setPage((current) => current + 1)}
                        >
                            Próxima
                        </button>
                    </div>
                )}
            </section>
        </div>
    )
}
