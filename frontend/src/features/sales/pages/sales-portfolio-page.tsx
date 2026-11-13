import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    getSalesProfile,
    listSalesPartnerOptions,
} from "../api/sales-api"

export function SalesPortfolioPage() {
    const { selectedCompany } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const [search, setSearch] = useState("")
    const [partnerId, setPartnerId] = useState("")

    const partners = useQuery({
        queryKey: ["sales-portfolio-partners", companyId, search] as const,
        queryFn: ({ signal }) =>
            listSalesPartnerOptions(companyId!, search, signal),
        enabled: Boolean(companyId),
        retry: false,
    })

    const profile = useQuery({
        queryKey: ["sales-profile", companyId, partnerId] as const,
        queryFn: () => getSalesProfile(companyId!, partnerId),
        enabled: Boolean(companyId && partnerId),
        retry: false,
    })

    const data = profile.isError ? undefined : profile.data

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="portfolioSearch">Buscar cliente</label>
                    <input
                        id="portfolioSearch"
                        type="search"
                        maxLength={100}
                        placeholder="Nome do cliente…"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                    />
                </div>

                <div className="f">
                    <label htmlFor="portfolioPartner">Cliente</label>
                    <select
                        id="portfolioPartner"
                        value={partnerId}
                        onChange={(event) => setPartnerId(event.target.value)}
                    >
                        <option value="">Selecione</option>
                        {(partners.data?.items ?? []).map((partner) => (
                            <option key={partner.id} value={partner.id}>
                                {partner.name}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {partners.isError ? (
                <QueryError
                    error={partners.error}
                    messages={{
                        403: "Você não tem acesso à carteira comercial desta empresa.",
                    }}
                    fallback="Não foi possível carregar os clientes do CRM."
                    onRetry={() => partners.refetch()}
                />
            ) : null}

            {profile.isError ? (
                <QueryError
                    error={profile.error}
                    messages={{
                        403: "Você não tem acesso à carteira comercial desta empresa.",
                        404: "O cliente selecionado não foi encontrado nesta empresa.",
                    }}
                    fallback="Não foi possível carregar a carteira do cliente."
                    onRetry={() => profile.refetch()}
                />
            ) : null}

            {!partnerId ? (
                <div className="empty">
                    Selecione um cliente para ver sua carteira comercial.
                </div>
            ) : profile.isPending ? (
                <div className="empty">Carregando…</div>
            ) : data ? (
                <>
                    <div className="panel">
                        <div className="ph">
                            <div>
                                <h2>{data.businessPartnerName}</h2>
                                <p className="help">
                                    Visão CRM em {data.asOf}. Dados financeiros e contratuais
                                    não são expostos sem as permissões desses módulos.
                                </p>
                            </div>
                            <span className={`badge ${data.active ? "b-ok" : "b-neu"}`}>
                                {data.active ? "Ativo" : "Inativo"}
                            </span>
                        </div>
                    </div>

                    <div className="cards">
                        <div className="card">
                            <div className="lb">Oportunidades abertas</div>
                            <div className="vl">{data.openOpportunities}</div>
                        </div>
                        <div className="card">
                            <div className="lb">Ganhas</div>
                            <div className="vl">{data.wonOpportunities}</div>
                        </div>
                        <div className="card">
                            <div className="lb">Perdidas</div>
                            <div className="vl">{data.lostOpportunities}</div>
                        </div>
                        <div className="card">
                            <div className="lb">Follow-ups pendentes</div>
                            <div className="vl">{data.openActivities}</div>
                            <div className="sub">
                                {data.overdueActivities} atrasado(s)
                            </div>
                        </div>
                    </div>
                </>
            ) : null}
        </>
    )
}
