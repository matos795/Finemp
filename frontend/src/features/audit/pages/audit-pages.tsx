import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
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

function AuditEventRow({ event }: { event: AuditEvent }) {
    const [expanded, setExpanded] = useState(false)
    const hasState = Boolean(event.beforeState || event.afterState)

    return (
        <>
            <tr>
                <td>{formatWhen(event.occurredAt)}</td>
                <td>
                    <span className="badge b-gold">
                        {labelAction(event.action)}
                    </span>
                </td>
                <td>
                    {event.resourceType ?? "—"}
                    {event.resourceId ? (
                        <div className="mut">
                            <code>{event.resourceId}</code>
                        </div>
                    ) : null}
                </td>
                <td>
                    <code>{event.actorUserAccountId ?? "sistema"}</code>
                </td>
                <td>
                    {event.companyId ? (
                        <code>{event.companyId}</code>
                    ) : (
                        <span className="mut">Global</span>
                    )}
                </td>
                <td>
                    {hasState || event.reason ? (
                        <button
                            type="button"
                            className="btn sec sm"
                            onClick={() => setExpanded((value) => !value)}
                        >
                            {expanded ? "Fechar" : "Detalhes"}
                        </button>
                    ) : (
                        <span className="mut">—</span>
                    )}
                </td>
            </tr>

            {expanded ? (
                <tr>
                    <td colSpan={6}>
                        <div className="grid2">
                            <div className="card">
                                <div className="lb">Antes</div>
                                <pre>{event.beforeState ?? "—"}</pre>
                            </div>
                            <div className="card">
                                <div className="lb">Depois</div>
                                <pre>{event.afterState ?? "—"}</pre>
                            </div>
                        </div>

                        {event.reason ? (
                            <p className="help">
                                Motivo: {event.reason}
                            </p>
                        ) : null}

                        <p className="help">
                            Usuário alvo:{" "}
                            <code>{event.subjectUserAccountId}</code>
                            {event.accountingFirmId ? (
                                <>
                                    {" "}· Escritório:{" "}
                                    <code>{event.accountingFirmId}</code>
                                </>
                            ) : null}
                            {event.commandId ? (
                                <>
                                    {" "}· Comando:{" "}
                                    <code>{event.commandId}</code>
                                </>
                            ) : null}
                        </p>
                    </td>
                </tr>
            ) : null}
        </>
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
        <>
            <Panel
                title={title}
                help={description}
            >
                <p className="help">
                    Consulta somente leitura. Os eventos são exibidos do mais
                    recente para o mais antigo.
                </p>
            </Panel>

            {query.isError ? (
                <QueryError
                    error={query.error}
                    messages={{
                        403: "Seu acesso não permite consultar esta auditoria.",
                    }}
                    fallback="Não foi possível consultar a auditoria."
                    onRetry={() => query.refetch()}
                />
            ) : null}

            {query.isPending ? (
                <div className="empty">Carregando atividades…</div>
            ) : null}

            {!query.isPending && !query.isError && result?.items.length === 0 ? (
                <div className="empty">
                    Nenhuma atividade auditada encontrada.
                </div>
            ) : null}

            {result && result.items.length > 0 ? (
                <Panel>
                    <div className="tblwrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Data e hora</th>
                                    <th>Ação</th>
                                    <th>Recurso</th>
                                    <th>Ator</th>
                                    <th>Company</th>
                                    <th>Detalhes</th>
                                </tr>
                            </thead>
                            <tbody>
                                {result.items.map((event) => (
                                    <AuditEventRow
                                        key={event.id}
                                        event={event}
                                    />
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {result.totalPages > 1 ? (
                        <div className="tools">
                            <button
                                type="button"
                                className="btn sec"
                                disabled={
                                    query.isFetching ||
                                    result.page === 0
                                }
                                onClick={() =>
                                    setPage((current) =>
                                        Math.max(0, current - 1),
                                    )
                                }
                            >
                                Anterior
                            </button>

                            <span className="sp" />

                            <span className="help">
                                Página {result.page + 1} de {result.totalPages}
                            </span>

                            <button
                                type="button"
                                className="btn sec"
                                disabled={
                                    query.isFetching ||
                                    result.page + 1 >= result.totalPages
                                }
                                onClick={() =>
                                    setPage((current) => current + 1)
                                }
                            >
                                Próxima
                            </button>
                        </div>
                    ) : null}
                </Panel>
            ) : null}
        </>
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
            <Panel
                title="Trilha de auditoria"
                help="Eventos auditados da Company selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para consultar os eventos auditados.
                </div>
            </Panel>
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
