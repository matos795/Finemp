import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { formatExactCurrency, formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    changeOpportunityStatus,
    createOpportunity,
    getOpportunity,
    listOpportunities,
    listSalesPartnerOptions,
    updateOpportunity,
} from "../api/sales-api"
import type {
    OpportunityStatus,
    OpportunitySummary,
    OpportunityValues,
    SalesPartnerOption,
} from "../types/sales.types"

const statusLabel: Record<OpportunityStatus, string> = {
    OPEN: "Aberta",
    WON: "Ganha",
    LOST: "Perdida",
}

const statusBadge: Record<OpportunityStatus, string> = {
    OPEN: "b-warn",
    WON: "b-ok",
    LOST: "b-bad",
}

function normalizeMoney(value: string) {
    return value.trim().replace(",", ".")
}

function validMoney(value: string) {
    return /^\d+(?:[.,]\d{1,2})?$/.test(value.trim())
}

type DialogState = { opportunity?: OpportunitySummary } | null

export function SalesPipelinePage() {
    const { selectedCompany } = useCompanyAccess()
    return <CompanyPipeline key={selectedCompany?.id ?? "no-company"} />
}

function CompanyPipeline() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = hasPermission("CRM_MANAGE")
    const queryClient = useQueryClient()
    const toast = useToast()

    const [search, setSearch] = useState("")
    const [status, setStatus] = useState<OpportunityStatus | "">("OPEN")
    const [stage, setStage] = useState("")
    const [filterSearch, setFilterSearch] = useState("")
    const [dialog, setDialog] = useState<DialogState>(null)
    const [page, setPage] = useCompanyPage(
        companyId,
        JSON.stringify({ filterSearch, status, stage }),
    )

    useEffect(() => {
        const timer = window.setTimeout(() => setFilterSearch(search.trim()), 350)
        return () => window.clearTimeout(timer)
    }, [search])

    const opportunities = useQuery({
        queryKey: ["sales-opportunities", companyId, filterSearch, status, stage, page] as const,
        queryFn: ({ signal }) =>
            listOpportunities(
                companyId!,
                { search: filterSearch, status, stage },
                page,
                signal,
            ),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const transition = useMutation({
        mutationFn: ({
            opportunity,
            next,
        }: {
            opportunity: OpportunitySummary
            next: OpportunityStatus
        }) => changeOpportunityStatus(companyId!, opportunity, next),
        onSuccess: async () => {
            await queryClient.invalidateQueries({
                queryKey: ["sales-opportunities", companyId],
            })
            toast("Situação da oportunidade atualizada.")
        },
    })

    const data = opportunities.isError ? undefined : opportunities.data
    const items = data?.items ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="salesStatus">Situação</label>
                    <select
                        id="salesStatus"
                        value={status}
                        onChange={(event) =>
                            setStatus(event.target.value as OpportunityStatus | "")
                        }
                    >
                        <option value="OPEN">Abertas</option>
                        <option value="WON">Ganhas</option>
                        <option value="LOST">Perdidas</option>
                        <option value="">Todas</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="salesStage">Etapa</label>
                    <input
                        id="salesStage"
                        maxLength={80}
                        placeholder="Todas"
                        value={stage}
                        onChange={(event) => setStage(event.target.value)}
                    />
                </div>

                <div className="f">
                    <label htmlFor="salesSearch">Buscar</label>
                    <input
                        id="salesSearch"
                        type="search"
                        maxLength={180}
                        placeholder="Oportunidade ou cliente…"
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
                            onClick={() => setDialog({})}
                        >
                            + Oportunidade
                        </button>
                    </div>
                ) : null}
            </div>

            {opportunities.isError ? (
                <QueryError
                    error={opportunities.error}
                    messages={{
                        400: "Confira os filtros do funil.",
                        403: "Você não tem acesso ao CRM desta empresa.",
                    }}
                    fallback="Não foi possível carregar o funil comercial."
                    onRetry={() => opportunities.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={opportunities.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Oportunidade</th>
                                    <th>Cliente</th>
                                    <th>Etapa</th>
                                    <th>Responsável</th>
                                    <th>Fechamento previsto</th>
                                    <th className="n">Mensal</th>
                                    <th className="n">Prob.</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((opportunity) => (
                                    <tr key={opportunity.id}>
                                        <td><b>{opportunity.title}</b></td>
                                        <td>{opportunity.businessPartnerName ?? "—"}</td>
                                        <td>{opportunity.stage}</td>
                                        <td>{opportunity.responsible}</td>
                                        <td>
                                            {opportunity.expectedCloseOn
                                                ? formatIsoDate(opportunity.expectedCloseOn)
                                                : "—"}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(opportunity.monthlyAmount)}
                                        </td>
                                        <td className="n">{opportunity.probability}%</td>
                                        <td>
                                            <span className={`badge ${statusBadge[opportunity.status]}`}>
                                                {statusLabel[opportunity.status]}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                                                    <button
                                                        className="btn sec sm"
                                                        type="button"
                                                        onClick={() => setDialog({ opportunity })}
                                                    >
                                                        Editar
                                                    </button>
                                                    {opportunity.status !== "WON" ? (
                                                        <button
                                                            className="btn sec sm"
                                                            type="button"
                                                            disabled={transition.isPending}
                                                            onClick={() =>
                                                                transition.mutate({
                                                                    opportunity,
                                                                    next: "WON",
                                                                })
                                                            }
                                                        >
                                                            Ganhar
                                                        </button>
                                                    ) : null}
                                                    {opportunity.status !== "LOST" ? (
                                                        <button
                                                            className="btn sec sm"
                                                            type="button"
                                                            disabled={transition.isPending}
                                                            onClick={() =>
                                                                transition.mutate({
                                                                    opportunity,
                                                                    next: "LOST",
                                                                })
                                                            }
                                                        >
                                                            Perder
                                                        </button>
                                                    ) : null}
                                                    {opportunity.status !== "OPEN" ? (
                                                        <button
                                                            className="btn sec sm"
                                                            type="button"
                                                            disabled={transition.isPending}
                                                            onClick={() =>
                                                                transition.mutate({
                                                                    opportunity,
                                                                    next: "OPEN",
                                                                })
                                                            }
                                                        >
                                                            Reabrir
                                                        </button>
                                                    ) : null}
                                                </div>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {opportunities.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">Nenhuma oportunidade encontrada.</div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={data?.totalPages ?? 0}
                        disabled={opportunities.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {transition.isError ? (
                <div className="alert e">
                    Não foi possível alterar a situação. Atualize a lista se outro
                    usuário tiver modificado a oportunidade.
                </div>
            ) : null}

            {dialog && companyId ? (
                <OpportunityDialog
                    key={dialog.opportunity
                        ? `${dialog.opportunity.id}-${dialog.opportunity.version}`
                        : "new"}
                    companyId={companyId}
                    opportunity={dialog.opportunity}
                    onClose={() => setDialog(null)}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({
                            queryKey: ["sales-opportunities", companyId],
                        })
                        toast(dialog.opportunity
                            ? "Oportunidade atualizada."
                            : "Oportunidade criada.")
                        setDialog(null)
                    }}
                />
            ) : null}
        </>
    )
}

function OpportunityDialog({
    companyId,
    opportunity,
    onClose,
    onSaved,
}: {
    companyId: string
    opportunity?: OpportunitySummary
    onClose: () => void
    onSaved: () => Promise<void>
}) {
    const [partnerSearch, setPartnerSearch] = useState("")
    const [businessPartnerId, setBusinessPartnerId] =
        useState<string | null>(opportunity ? null : "")
    const [selectedPartner, setSelectedPartner] = useState<SalesPartnerOption | null>(null)
    const [title, setTitle] = useState<string | null>(opportunity ? null : "")
    const [stage, setStage] = useState<string | null>(opportunity ? null : "Prospecção")
    const [monthlyAmount, setMonthlyAmount] =
        useState<string | null>(opportunity ? null : "")
    const [probability, setProbability] =
        useState<string | null>(opportunity ? null : "25")
    const [responsible, setResponsible] = useState<string | null>(opportunity ? null : "")
    const [expectedCloseOn, setExpectedCloseOn] =
        useState<string | null>(opportunity ? null : "")
    const [notes, setNotes] = useState<string | null>(opportunity ? null : "")
    const [error, setError] = useState("")

    const detail = useQuery({
        queryKey: ["sales-opportunity", companyId, opportunity?.id] as const,
        queryFn: () => getOpportunity(companyId, opportunity!.id),
        enabled: Boolean(opportunity),
        retry: false,
    })


    const partners = useQuery({
        queryKey: ["sales-partners", companyId, partnerSearch] as const,
        queryFn: ({ signal }) =>
            listSalesPartnerOptions(companyId, partnerSearch, signal),
        retry: false,
    })

    const effectiveBusinessPartnerId =
        businessPartnerId ?? detail.data?.businessPartnerId ?? ""
    const effectiveTitle = title ?? detail.data?.title ?? ""
    const effectiveStage = stage ?? detail.data?.stage ?? ""
    const effectiveMonthlyAmount = monthlyAmount ?? detail.data?.monthlyAmount ?? ""
    const effectiveProbability = probability ?? String(detail.data?.probability ?? "")
    const effectiveResponsible = responsible ?? detail.data?.responsible ?? ""
    const effectiveExpectedCloseOn =
        expectedCloseOn ?? detail.data?.expectedCloseOn ?? ""
    const effectiveNotes = notes ?? detail.data?.notes ?? ""

    const save = useMutation({
        mutationFn: async () => {
            setError("")
            const probabilityNumber = Number(effectiveProbability)
            if (
                effectiveTitle.trim().length < 2 ||
                !effectiveStage.trim() ||
                !validMoney(effectiveMonthlyAmount) ||
                !Number.isInteger(probabilityNumber) ||
                probabilityNumber < 0 ||
                probabilityNumber > 100 ||
                !effectiveResponsible.trim()
            ) {
                throw new Error("VALIDATION")
            }

            const values: OpportunityValues = {
                businessPartnerId: effectiveBusinessPartnerId || null,
                title: effectiveTitle.trim(),
                stage: effectiveStage.trim(),
                monthlyAmount: normalizeMoney(effectiveMonthlyAmount),
                probability: probabilityNumber,
                responsible: effectiveResponsible.trim(),
                expectedCloseOn: effectiveExpectedCloseOn || null,
                notes: effectiveNotes.trim() || null,
            }

            if (opportunity && !detail.data) {
                throw new Error("DETAIL_NOT_READY")
            }

            return opportunity
                ? updateOpportunity(companyId, detail.data!, values)
                : createOpportunity(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: (caught) => {
            const status =
                typeof caught === "object" && caught !== null && "status" in caught
                    ? Number((caught as { status: unknown }).status)
                    : null
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Confira título, etapa, valor, probabilidade e responsável.")
            } else if (caught instanceof Error && caught.message === "DETAIL_NOT_READY") {
                setError("Carregue os dados atuais da oportunidade antes de salvar.")
            } else if (status === 409) {
                setError(
                    "A oportunidade mudou ou possui follow-ups vinculados a outro cliente. Atualize e confira os vínculos.",
                )
            } else if (status === 403) {
                setError("Seu acesso não permite alterar o CRM.")
            } else {
                setError("Não foi possível salvar a oportunidade.")
            }
        },
    })

    const partnerItems = partners.data?.items ?? []
    const detailPartner =
        businessPartnerId === null && detail.data?.businessPartnerId
            ? {
                id: detail.data.businessPartnerId,
                name: detail.data.businessPartnerName ?? "Parceiro selecionado",
            }
            : null
    const effectiveSelectedPartner = selectedPartner ?? detailPartner
    const selectedMissing =
        effectiveSelectedPartner &&
        !partnerItems.some((partner) => partner.id === effectiveSelectedPartner.id)

    if (opportunity && detail.isPending) {
        return (
            <Modal onClose={onClose}>
                <div className="empty">Carregando oportunidade…</div>
            </Modal>
        )
    }

    if (opportunity && detail.isError) {
        return (
            <Modal onClose={onClose}>
                <div className="alert e">Não foi possível carregar a oportunidade atual.</div>
                <div className="ma">
                    <button type="button" className="btn sec" onClick={onClose}>
                        Fechar
                    </button>
                    <button type="button" className="btn" onClick={() => detail.refetch()}>
                        Tentar novamente
                    </button>
                </div>
            </Modal>
        )
    }

    return (
        <Modal onClose={onClose} labelledBy="opportunityDialogTitle">
                <h2 id="opportunityDialogTitle">
                    {opportunity ? "Editar oportunidade" : "Nova oportunidade"}
                </h2>

                <div className="fg">
                    <div className="fl full">
                        <label htmlFor="opportunityTitle">Título</label>
                        <input
                            id="opportunityTitle"
                            maxLength={180}
                            value={effectiveTitle}
                            onChange={(event) => setTitle(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityStage">Etapa</label>
                        <input
                            id="opportunityStage"
                            maxLength={80}
                            value={effectiveStage}
                            onChange={(event) => setStage(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityResponsible">Responsável</label>
                        <input
                            id="opportunityResponsible"
                            maxLength={160}
                            value={effectiveResponsible}
                            onChange={(event) => setResponsible(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityAmount">Valor mensal</label>
                        <input
                            id="opportunityAmount"
                            inputMode="decimal"
                            value={effectiveMonthlyAmount}
                            onChange={(event) => setMonthlyAmount(event.target.value)}
                            placeholder="0,00"
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityProbability">Probabilidade (%)</label>
                        <input
                            id="opportunityProbability"
                            type="number"
                            min="0"
                            max="100"
                            value={effectiveProbability}
                            onChange={(event) => setProbability(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityExpectedClose">Fechamento previsto</label>
                        <input
                            id="opportunityExpectedClose"
                            type="date"
                            value={effectiveExpectedCloseOn}
                            onChange={(event) => setExpectedCloseOn(event.target.value)}
                        />
                    </div>

                    <div className="fl">
                        <label htmlFor="opportunityPartnerSearch">Buscar cliente</label>
                        <input
                            id="opportunityPartnerSearch"
                            type="search"
                            value={partnerSearch}
                            onChange={(event) => setPartnerSearch(event.target.value)}
                        />
                    </div>

                    <div className="fl full">
                        <label htmlFor="opportunityPartner">Cliente vinculado (opcional)</label>
                        <select
                            id="opportunityPartner"
                            value={effectiveBusinessPartnerId}
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
                            <option value="">Sem cliente vinculado</option>
                            {effectiveSelectedPartner && selectedMissing ? (
                                <option value={effectiveSelectedPartner.id}>
                                    {effectiveSelectedPartner.name}
                                </option>
                            ) : null}
                            {partnerItems.map((partner) => (
                                <option key={partner.id} value={partner.id}>
                                    {partner.name}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="fl full">
                        <label htmlFor="opportunityNotes">Observações</label>
                        <textarea
                            id="opportunityNotes"
                            maxLength={4000}
                            value={effectiveNotes}
                            onChange={(event) => setNotes(event.target.value)}
                        />
                    </div>
                </div>

                {error ? <div className="alert e">{error}</div> : null}

                <div className="ma">
                    <button
                        type="button"
                        className="btn sec"
                        disabled={save.isPending}
                        onClick={onClose}
                    >
                        Cancelar
                    </button>
                    <button
                        type="button"
                        className="btn"
                        disabled={save.isPending || Boolean(opportunity && detail.isPending)}
                        onClick={() => save.mutate()}
                    >
                        {save.isPending ? "Salvando…" : "Salvar"}
                    </button>
                </div>
        </Modal>
    )
}
