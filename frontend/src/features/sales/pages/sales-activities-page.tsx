import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    changeSalesActivityCompletion,
    createSalesActivity,
    listOpportunities,
    listSalesActivities,
    listSalesPartnerOptions,
} from "../api/sales-api"
import type {
    OpportunitySummary,
    SalesActivity,
    SalesActivityValues,
    SalesPartnerOption,
} from "../types/sales.types"

const dueLabel = {
    OVERDUE: "Atrasada",
    TODAY: "Hoje",
    UPCOMING: "Próxima",
    COMPLETED: "Concluída",
} as const

const dueBadge = {
    OVERDUE: "b-bad",
    TODAY: "b-warn",
    UPCOMING: "b-neu",
    COMPLETED: "b-ok",
} as const

export function SalesActivitiesPage() {
    const { selectedCompany } = useCompanyAccess()
    return <CompanyActivities key={selectedCompany?.id ?? "no-company"} />
}

function CompanyActivities() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = hasPermission("CRM_MANAGE")
    const queryClient = useQueryClient()
    const toast = useToast()

    const [search, setSearch] = useState("")
    const [filterSearch, setFilterSearch] = useState("")
    const [completed, setCompleted] = useState<"" | "true" | "false">("false")
    const [dialogOpen, setDialogOpen] = useState(false)
    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ filterSearch, completed }),
    )

    useEffect(() => {
        const timer = window.setTimeout(() => setFilterSearch(search.trim()), 350)
        return () => window.clearTimeout(timer)
    }, [search])

    const activities = useQuery({
        queryKey: ["sales-activities", companyId, filterSearch, completed, page] as const,
        queryFn: ({ signal }) =>
            listSalesActivities(
                companyId!,
                { search: filterSearch, completed },
                page,
                signal,
            ),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const completion = useMutation({
        mutationFn: ({
            activity,
            done,
        }: {
            activity: SalesActivity
            done: boolean
        }) => changeSalesActivityCompletion(companyId!, activity, done),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: ["sales-activities", companyId],
            })
            toast("Follow-up atualizado.")
        },
    })

    const data = activities.isError ? undefined : activities.data
    const items = data?.items ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="activityState">Situação</label>
                    <select
                        id="activityState"
                        value={completed}
                        onChange={(event) =>
                            setCompleted(event.target.value as "" | "true" | "false")
                        }
                    >
                        <option value="false">Pendentes</option>
                        <option value="true">Concluídas</option>
                        <option value="">Todas</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="activitySearch">Buscar</label>
                    <input
                        id="activitySearch"
                        type="search"
                        maxLength={180}
                        placeholder="Assunto, cliente ou oportunidade…"
                        value={search}
                        onChange={(event) => setSearch(event.target.value)}
                    />
                </div>

                {canManage ? (
                    <div className="f">
                        <label>&nbsp;</label>
                        <button
                            type="button"
                            className="btn"
                            onClick={() => setDialogOpen(true)}
                        >
                            + Follow-up
                        </button>
                    </div>
                ) : null}
            </div>

            {activities.isError ? (
                <QueryError
                    error={activities.error}
                    messages={{
                        400: "Confira os filtros dos follow-ups.",
                        403: "Você não tem acesso ao CRM desta empresa.",
                    }}
                    fallback="Não foi possível carregar os follow-ups."
                    onRetry={() => activities.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={activities.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Prazo</th>
                                    <th>Follow-up</th>
                                    <th>Cliente</th>
                                    <th>Oportunidade</th>
                                    <th>Responsável</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((activity) => (
                                    <tr key={activity.id}>
                                        <td>{formatIsoDate(activity.dueOn)}</td>
                                        <td><b>{activity.subject}</b></td>
                                        <td>{activity.businessPartnerName ?? "—"}</td>
                                        <td>{activity.opportunityTitle ?? "—"}</td>
                                        <td>{activity.responsible}</td>
                                        <td>
                                            <span className={`badge ${dueBadge[activity.dueStatus]}`}>
                                                {dueLabel[activity.dueStatus]}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    disabled={completion.isPending}
                                                    onClick={() =>
                                                        completion.mutate({
                                                            activity,
                                                            done: !activity.completedAt,
                                                        })
                                                    }
                                                >
                                                    {activity.completedAt ? "Reabrir" : "Concluir"}
                                                </button>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {activities.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">Nenhum follow-up encontrado.</div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={data?.totalPages ?? 0}
                        disabled={activities.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {completion.isError ? (
                <div className="alert e">
                    Não foi possível atualizar o follow-up. A versão pode ter mudado.
                </div>
            ) : null}

            {dialogOpen && companyId ? (
                <ActivityDialog
                    companyId={companyId}
                    onClose={() => setDialogOpen(false)}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({
                            queryKey: ["sales-activities", companyId],
                        })
                        toast("Follow-up criado.")
                        setDialogOpen(false)
                    }}
                />
            ) : null}
        </>
    )
}

function ActivityDialog({
    companyId,
    onClose,
    onSaved,
}: {
    companyId: string
    onClose: () => void
    onSaved: () => Promise<void>
}) {
    const [subject, setSubject] = useState("")
    const [responsible, setResponsible] = useState("")
    const [dueOn, setDueOn] = useState("")
    const [notes, setNotes] = useState("")
    const [partnerSearch, setPartnerSearch] = useState("")
    const [businessPartnerId, setBusinessPartnerId] = useState("")
    const [selectedPartner, setSelectedPartner] = useState<SalesPartnerOption | null>(null)
    const [opportunityId, setOpportunityId] = useState("")
    const [selectedOpportunity, setSelectedOpportunity] =
        useState<OpportunitySummary | null>(null)
    const [opportunitySearch, setOpportunitySearch] = useState("")
    const [opportunityPage, setOpportunityPage] = useState(0)
    const [error, setError] = useState("")

    const partners = useQuery({
        queryKey: ["sales-partners", companyId, partnerSearch] as const,
        queryFn: ({ signal }) =>
            listSalesPartnerOptions(companyId, partnerSearch, signal),
        retry: false,
    })

    const opportunities = useQuery({
        queryKey: ["activity-opportunities", companyId, opportunitySearch, opportunityPage] as const,
        queryFn: ({ signal }) =>
            listOpportunities(
                companyId,
                { status: "OPEN", search: opportunitySearch.trim() },
                opportunityPage,
                signal,
            ),
        retry: false,
    })

    const save = useMutation({
        mutationFn: () => {
            setError("")
            if (subject.trim().length < 2 || !responsible.trim() || !dueOn) {
                throw new Error("VALIDATION")
            }

            const values: SalesActivityValues = {
                opportunityId: opportunityId || null,
                businessPartnerId: businessPartnerId || null,
                subject: subject.trim(),
                responsible: responsible.trim(),
                dueOn,
                notes: notes.trim() || null,
            }
            return createSalesActivity(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: (caught) => {
            const status =
                typeof caught === "object" && caught !== null && "status" in caught
                    ? Number((caught as { status: unknown }).status)
                    : null
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Informe assunto, responsável e prazo.")
            } else if (status === 403) {
                setError("Seu acesso não permite alterar o CRM.")
            } else {
                setError("Não foi possível criar o follow-up.")
            }
        },
    })

    const partnerItems = partners.data?.items ?? []
    const opportunityItems: OpportunitySummary[] = opportunities.data?.items ?? []
    const selectedMissing =
        selectedPartner &&
        !partnerItems.some((partner) => partner.id === selectedPartner.id)
    const selectedOpportunityMissing =
        selectedOpportunity &&
        !opportunityItems.some((opportunity) => opportunity.id === selectedOpportunity.id)

    return (
        <div className="modal" role="presentation">
            <div className="mbox" role="dialog" aria-modal="true" aria-labelledby="activityDialogTitle">
                <h2 id="activityDialogTitle">Novo follow-up</h2>

                <div className="fg">
                    <div className="fl full">
                        <label htmlFor="activitySubject">Assunto</label>
                        <input
                            id="activitySubject"
                            maxLength={180}
                            value={subject}
                            onChange={(event) => setSubject(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="activityResponsible">Responsável</label>
                        <input
                            id="activityResponsible"
                            maxLength={160}
                            value={responsible}
                            onChange={(event) => setResponsible(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="activityDue">Prazo</label>
                        <input
                            id="activityDue"
                            type="date"
                            value={dueOn}
                            onChange={(event) => setDueOn(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="activityPartnerSearch">Buscar cliente</label>
                        <input
                            id="activityPartnerSearch"
                            type="search"
                            value={partnerSearch}
                            onChange={(event) => setPartnerSearch(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="activityPartner">Cliente (opcional)</label>
                        <select
                            id="activityPartner"
                            value={businessPartnerId}
                            onChange={(event) => {
                                const id = event.target.value
                                setBusinessPartnerId(id)
                                if (!id) {
                                    setSelectedPartner(null)
                                    return
                                }
                                const partner = partnerItems.find((candidate) => candidate.id === id)
                                if (partner) setSelectedPartner(partner)
                            }}
                        >
                            <option value="">Sem cliente</option>
                            {selectedMissing ? (
                                <option value={selectedPartner.id}>{selectedPartner.name}</option>
                            ) : null}
                            {partnerItems.map((partner) => (
                                <option key={partner.id} value={partner.id}>
                                    {partner.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="fl full">
                        <label htmlFor="activityOpportunitySearch">Buscar oportunidade</label>
                        <input
                            id="activityOpportunitySearch"
                            type="search"
                            maxLength={180}
                            value={opportunitySearch}
                            onChange={(event) => {
                                setOpportunitySearch(event.target.value)
                                setOpportunityPage(0)
                            }}
                        />
                    </div>

                    <div className="fl full">
                        <label htmlFor="activityOpportunity">Oportunidade (opcional)</label>
                        <select
                            id="activityOpportunity"
                            value={opportunityId}
                            onChange={(event) => {
                                const id = event.target.value
                                setOpportunityId(id)
                                if (!id) {
                                    setSelectedOpportunity(null)
                                    return
                                }
                                const opportunity = opportunityItems.find(
                                    (candidate) => candidate.id === id,
                                )
                                if (opportunity) setSelectedOpportunity(opportunity)
                            }}
                        >
                            <option value="">Sem oportunidade</option>
                            {selectedOpportunityMissing ? (
                                <option value={selectedOpportunity.id}>
                                    {selectedOpportunity.title}
                                </option>
                            ) : null}
                            {opportunityItems.map((opportunity) => (
                                <option key={opportunity.id} value={opportunity.id}>
                                    {opportunity.title}
                                </option>
                            ))}
                        </select>
                        <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                            <button
                                type="button"
                                className="btn sec sm"
                                disabled={opportunityPage <= 0 || opportunities.isFetching}
                                onClick={() => setOpportunityPage((current) => Math.max(0, current - 1))}
                            >
                                Anterior
                            </button>
                            <button
                                type="button"
                                className="btn sec sm"
                                disabled={
                                    opportunities.isFetching ||
                                    opportunityPage + 1 >= (opportunities.data?.totalPages ?? 0)
                                }
                                onClick={() => setOpportunityPage((current) => current + 1)}
                            >
                                Próxima
                            </button>
                        </div>
                    </div>

                    <div className="fl full">
                        <label htmlFor="activityNotes">Observações</label>
                        <textarea
                            id="activityNotes"
                            maxLength={4000}
                            value={notes}
                            onChange={(event) => setNotes(event.target.value)}
                        />
                    </div>
                </div>

                {error ? <div className="alert e">{error}</div> : null}

                <div className="ma">
                    <button className="btn sec" type="button" onClick={onClose}>
                        Cancelar
                    </button>
                    <button
                        className="btn"
                        type="button"
                        disabled={save.isPending}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Salvando…" : "Salvar"}
                    </button>
                </div>
            </div>
        </div>
    )
}
