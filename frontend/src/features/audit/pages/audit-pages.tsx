import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    listCompanyAuditEvents,
    listGlobalAuditEvents,
    type AuditEvent,
} from "../api/audit-api"

const PAGE_SIZE = 50

function formatWhen(value: string) {
    const date = new Date(value)
    return Number.isNaN(date.getTime())
        ? value
        : date.toLocaleString("pt-BR")
}

function labelAction(action: string) {
    return action
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ")
}

function AuditEventCard({ event }: { event: AuditEvent }) {
    const [expanded, setExpanded] = useState(false)

    return (
        <article className="card">
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    gap: 12,
                    flexWrap: "wrap",
                }}
            >
                <strong>{labelAction(event.action)}</strong>
                <small>{formatWhen(event.occurredAt)}</small>
            </div>

            <div style={{ marginTop: 8 }}>
                {event.resourceType && (
                    <div>
                        Recurso: <code>{event.resourceType}</code>
                    </div>
                )}
                {event.resourceId && (
                    <div>
                        ID do recurso: <code>{event.resourceId}</code>
                    </div>
                )}
                {event.companyId && (
                    <div>
                        Company: <code>{event.companyId}</code>
                    </div>
                )}
                {event.accountingFirmId && (
                    <div>
                        Escritório: <code>{event.accountingFirmId}</code>
                    </div>
                )}
                <div>
                    Ator: <code>{event.actorUserAccountId ?? "sistema"}</code>
                </div>
                <div>
                    Usuário alvo: <code>{event.subjectUserAccountId}</code>
                </div>
                {event.reason && <div>Motivo: {event.reason}</div>}
            </div>

            {(event.beforeState || event.afterState) && (
                <div style={{ marginTop: 10 }}>
                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => setExpanded((value) => !value)}
                    >
                        {expanded ? "Ocultar estados" : "Ver estados auditados"}
                    </button>

                    {expanded && (
                        <div
                            style={{
                                display: "grid",
                                gridTemplateColumns:
                                    "repeat(auto-fit, minmax(280px, 1fr))",
                                gap: 10,
                                marginTop: 10,
                            }}
                        >
                            <div>
                                <strong>Antes</strong>
                                <pre style={{ whiteSpace: "pre-wrap" }}>
                                    {event.beforeState ?? "—"}
                                </pre>
                            </div>
                            <div>
                                <strong>Depois</strong>
                                <pre style={{ whiteSpace: "pre-wrap" }}>
                                    {event.afterState ?? "—"}
                                </pre>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </article>
    )
}

function AuditLogView({
    companyId,
    title,
    description,
}: {
    companyId?: string
    title: string
    description: string
}) {
    const [page, setPage] = useState(0)

    const query = useQuery({
        queryKey: ["audit-events", companyId ?? "global", page] as const,
        queryFn: () =>
            companyId
                ? listCompanyAuditEvents(companyId, page, PAGE_SIZE)
                : listGlobalAuditEvents(page, PAGE_SIZE),
        retry: false,
    })

    const result = query.data

    return (
        <div>
            <section className="mbox">
                <h1>{title}</h1>
                <p className="help">{description}</p>
            </section>

            <section className="mbox">
                {query.isLoading && <p>Carregando atividades...</p>}

                {query.isError && (
                    <div className="alert e">
                        Não foi possível consultar a auditoria.
                    </div>
                )}

                {!query.isLoading &&
                    !query.isError &&
                    result?.items.length === 0 && (
                        <p className="help">
                            Nenhuma atividade auditada encontrada.
                        </p>
                    )}

                <div style={{ display: "grid", gap: 10 }}>
                    {result?.items.map((event) => (
                        <AuditEventCard key={event.id} event={event} />
                    ))}
                </div>

                {result && result.totalPages > 1 && (
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            gap: 8,
                            marginTop: 14,
                        }}
                    >
                        <button
                            type="button"
                            className="btn sec"
                            disabled={query.isFetching || result.page === 0}
                            onClick={() =>
                                setPage((current) => Math.max(0, current - 1))
                            }
                        >
                            Anterior
                        </button>

                        <small>
                            Página {result.page + 1} de {result.totalPages}
                        </small>

                        <button
                            type="button"
                            className="btn sec"
                            disabled={
                                query.isFetching ||
                                result.page + 1 >= result.totalPages
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

export function PlatformActivityLogPage() {
    return (
        <AuditLogView
            title="Registro de atividades"
            description="Eventos administrativos e financeiros auditados em toda a plataforma."
        />
    )
}

export function CompanyAuditTrailPage() {
    const { selectedCompany } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <section className="mbox">
                <h1>Trilha de auditoria</h1>
                <p className="help">
                    Selecione uma empresa para consultar os eventos auditados.
                </p>
            </section>
        )
    }

    return (
        <AuditLogView
            companyId={selectedCompany.id}
            title="Trilha de auditoria"
            description={`Eventos auditados exclusivamente da empresa ${selectedCompany.name}.`}
        />
    )
}
