import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyAccess } from "../../companies/company-access-context"
import { listCompanyAuditEvents } from "../api/audit-api"
import { AuditEventCard } from "./platform-audit-page"

const PAGE_SIZE = 50

function CompanyAuditForCompany({ companyId }: { companyId: string }) {
    const [page, setPage] = useState(0)

    const query = useQuery({
        queryKey: ["company-audit-events", companyId, page] as const,
        queryFn: () => listCompanyAuditEvents(companyId, page, PAGE_SIZE),
        retry: false,
    })

    const error =
        query.error instanceof ApiError && query.error.status === 403
            ? "A trilha de auditoria ainda é restrita a SYSTEM_ADMIN."
            : query.error
              ? "Não foi possível carregar a trilha de auditoria."
              : ""

    return (
        <div>
            <section className="mbox">
                <h1>Trilha de auditoria</h1>
                <p className="help">
                    Eventos do journal filtrados no servidor para a Company
                    selecionada. Nenhum estado sensível é filtrado no React.
                </p>
                {error && <div className="alert e">{error}</div>}
            </section>

            <section className="mbox">
                {query.isLoading && <p>Carregando...</p>}
                {!query.isLoading && query.data?.items.length === 0 && (
                    <p className="help">Nenhum evento auditado para esta Company.</p>
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

export function CompanyAuditPage() {
    const { selectedCompany } = useCompanyAccess()

    if (!selectedCompany) {
        return (
            <div className="mbox">
                Selecione uma empresa para consultar a trilha de auditoria.
            </div>
        )
    }

    return (
        <CompanyAuditForCompany
            key={selectedCompany.id}
            companyId={selectedCompany.id}
        />
    )
}
