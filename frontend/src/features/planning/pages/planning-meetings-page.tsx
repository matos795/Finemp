import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createPlanningMeeting,
    deletePlanningMeeting,
    listPlanningMeetings,
    updatePlanningMeeting,
    type PlanningMeeting,
} from "../api/planning-api.ts"
import {
    canManagePlanning,
    canReadPlanning,
    describePlanningMutationError,
    reconcilePlanningPage,
} from "../lib/planning-ui.ts"

const EMPTY_FORM = {
    title: "",
    stage: "",
    scheduledOn: "",
    notes: "",
    diagnosticText: "",
}

export function PlanningMeetingsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <PlanningMeetingsCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function PlanningMeetingsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const queryClient = useQueryClient()
    const companyId = selectedCompany?.id
    const canRead = canReadPlanning(hasPermission)
    const canManage = canManagePlanning(hasPermission)

    const [stageInput, setStageInput] = useState("")
    const [stage, setStage] = useState("")
    const [page, setPage] = useState(0)
    const [editing, setEditing] = useState<PlanningMeeting | null>(null)
    const [formOpen, setFormOpen] = useState(false)
    const [form, setForm] = useState(EMPTY_FORM)
    const [busy, setBusy] = useState(false)
    const [mutationError, setMutationError] = useState("")

    const query = useQuery({
        queryKey: ["planning-meetings", companyId, stage, page] as const,
        queryFn: () => listPlanningMeetings(companyId!, stage, page, 50),
        enabled: Boolean(companyId && canRead),
        retry: false,
    })

    if (!selectedCompany) {
        return (
            <Panel
                title="Cronograma de encontros"
                help="Agenda do processo de planejamento da empresa selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para consultar o planejamento.
                </div>
            </Panel>
        )
    }

    if (!canRead) {
        return (
            <div className="alert i">
                Seu acesso não inclui o planejamento desta empresa.
            </div>
        )
    }

    function openCreate() {
        setEditing(null)
        setForm(EMPTY_FORM)
        setMutationError("")
        setFormOpen(true)
    }

    function openEdit(meeting: PlanningMeeting) {
        setEditing(meeting)
        setForm({
            title: meeting.title,
            stage: meeting.stage,
            scheduledOn: meeting.scheduledOn,
            notes: meeting.notes ?? "",
            diagnosticText: meeting.diagnosticText ?? "",
        })
        setMutationError("")
        setFormOpen(true)
    }

    async function save() {
        if (!companyId) return

        setBusy(true)
        setMutationError("")

        try {
            if (editing) {
                await updatePlanningMeeting(
                    companyId,
                    editing.id,
                    {
                        ...editing,
                        title: form.title.trim(),
                        stage: form.stage.trim(),
                        scheduledOn: form.scheduledOn,
                        notes: form.notes.trim() || null,
                        diagnosticText:
                            form.diagnosticText.trim() || null,
                    },
                )
            } else {
                await createPlanningMeeting(companyId, {
                    title: form.title.trim(),
                    stage: form.stage.trim(),
                    scheduledOn: form.scheduledOn,
                    notes: form.notes.trim() || null,
                    diagnosticText:
                        form.diagnosticText.trim() || null,
                })
            }

            await queryClient.invalidateQueries({
                queryKey: ["planning-meetings", companyId],
            })
            setFormOpen(false)
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    async function remove(meeting: PlanningMeeting) {
        if (
            !companyId ||
            !window.confirm(
                `Excluir o encontro "${meeting.title}"?`,
            )
        ) {
            return
        }

        setBusy(true)
        setMutationError("")

        try {
            await deletePlanningMeeting(
                companyId,
                meeting.id,
                meeting.version,
            )
            const refreshed = await query.refetch()
            const nextPage = reconcilePlanningPage(
                page,
                refreshed.data?.totalPages ?? 0,
                refreshed.data?.items.length ?? 0,
            )

            await queryClient.invalidateQueries({
                queryKey: ["planning-meetings", companyId],
            })

            if (nextPage !== page) {
                setPage(nextPage)
            }
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    const result = query.data

    return (
        <>
            <Panel
                title="Cronograma de encontros"
                help="Agenda e diagnóstico dos encontros do processo de planejamento."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="planningMeetingStage">
                            Etapa
                        </label>
                        <input
                            id="planningMeetingStage"
                            type="search"
                            value={stageInput}
                            placeholder="Todas as etapas"
                            onChange={(event) =>
                                setStageInput(event.target.value)
                            }
                        />
                    </div>

                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => {
                            setPage(0)
                            setStage(stageInput.trim())
                        }}
                    >
                        Filtrar
                    </button>

                    <span style={{ flex: 1 }} />

                    {canManage ? (
                        <button
                            type="button"
                            className="btn"
                            onClick={openCreate}
                        >
                            Novo encontro
                        </button>
                    ) : null}
                </div>

                {mutationError ? (
                    <div className="alert e">{mutationError}</div>
                ) : null}
            </Panel>

            {query.isError ? (
                <QueryError
                    error={query.error}
                    messages={{
                        403: "Seu acesso não permite consultar o cronograma.",
                    }}
                    fallback="Não foi possível carregar o cronograma."
                    onRetry={() => query.refetch()}
                />
            ) : null}

            {query.isPending ? (
                <div className="empty">Carregando encontros…</div>
            ) : null}

            {!query.isPending &&
            !query.isError &&
            result?.items.length === 0 ? (
                <div className="empty">
                    Nenhum encontro encontrado.
                </div>
            ) : null}

            {result && result.items.length > 0 ? (
                <Panel>
                    <div className="tblwrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Data</th>
                                    <th>Etapa</th>
                                    <th>Encontro</th>
                                    <th>Diagnóstico</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {result.items.map((meeting) => (
                                    <tr key={meeting.id}>
                                        <td>{meeting.scheduledOn}</td>
                                        <td>
                                            <span className="badge b-gold">
                                                {meeting.stage}
                                            </span>
                                        </td>
                                        <td>
                                            <b>{meeting.title}</b>
                                            {meeting.notes ? (
                                                <div className="mut">
                                                    {meeting.notes}
                                                </div>
                                            ) : null}
                                        </td>
                                        <td>
                                            {meeting.diagnosticText ?? (
                                                <span className="mut">—</span>
                                            )}
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <div className="tools">
                                                    <button
                                                        type="button"
                                                        className="btn sec sm"
                                                        onClick={() =>
                                                            openEdit(meeting)
                                                        }
                                                    >
                                                        Editar
                                                    </button>
                                                    <button
                                                        type="button"
                                                        className="btn danger sm"
                                                        disabled={busy}
                                                        onClick={() =>
                                                            void remove(meeting)
                                                        }
                                                    >
                                                        Excluir
                                                    </button>
                                                </div>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {result.totalPages > 1 ? (
                        <div className="tools">
                            <button
                                className="btn sec"
                                type="button"
                                disabled={
                                    query.isFetching || result.page === 0
                                }
                                onClick={() =>
                                    setPage((current) =>
                                        Math.max(0, current - 1),
                                    )
                                }
                            >
                                Anterior
                            </button>
                            <span style={{ flex: 1 }} />
                            <span className="help">
                                Página {result.page + 1} de {result.totalPages}
                            </span>
                            <button
                                className="btn sec"
                                type="button"
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

            {formOpen ? (
                <div className="modal" role="presentation">
                    <div
                        className="mbox"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="planningMeetingDialogTitle"
                    >
                        <h2 id="planningMeetingDialogTitle">
                            {editing ? "Editar encontro" : "Novo encontro"}
                        </h2>

                        <div className="fg">
                            <div className="fl full">
                                <label htmlFor="meetingTitle">
                                    Título
                                </label>
                                <input
                                    id="meetingTitle"
                                    type="text"
                                    value={form.title}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            title: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="meetingStage">
                                    Etapa
                                </label>
                                <input
                                    id="meetingStage"
                                    type="text"
                                    value={form.stage}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            stage: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="meetingDate">
                                    Data
                                </label>
                                <input
                                    id="meetingDate"
                                    type="date"
                                    value={form.scheduledOn}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            scheduledOn: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="meetingNotes">
                                    Observações
                                </label>
                                <textarea
                                    id="meetingNotes"
                                    value={form.notes}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            notes: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="meetingDiagnostic">
                                    Diagnóstico
                                </label>
                                <textarea
                                    id="meetingDiagnostic"
                                    value={form.diagnosticText}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            diagnosticText:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>
                        </div>

                        {mutationError ? (
                            <div className="alert e">
                                {mutationError}
                            </div>
                        ) : null}

                        <div className="mact">
                            <button
                                type="button"
                                className="btn sec"
                                disabled={busy}
                                onClick={() => setFormOpen(false)}
                            >
                                Cancelar
                            </button>
                            <button
                                type="button"
                                className="btn"
                                disabled={
                                    busy ||
                                    !form.title.trim() ||
                                    !form.stage.trim() ||
                                    !form.scheduledOn
                                }
                                onClick={() => void save()}
                            >
                                {busy ? "Salvando…" : "Salvar"}
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}
        </>
    )
}
