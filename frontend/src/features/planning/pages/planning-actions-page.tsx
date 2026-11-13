import { useMemo, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { formatExactCurrency } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createActionPlan,
    deleteActionPlan,
    listActionPlans,
    listAllActiveGoals,
    setActionCompleted,
    updateActionPlan,
    type ActionPlanItem,
} from "../api/planning-api.ts"
import {
    canManagePlanning,
    canReadPlanning,
    describePlanningMutationError,
    reconcilePlanningPage,
} from "../lib/planning-ui.ts"

interface ActionForm {
    goalId: string
    what: string
    why: string
    responsible: string
    dueOn: string
    where: string
    how: string
    cost: string
    workflowStatus: string
}

const EMPTY_FORM: ActionForm = {
    goalId: "",
    what: "",
    why: "",
    responsible: "",
    dueOn: "",
    where: "",
    how: "",
    cost: "",
    workflowStatus: "",
}

export function PlanningActionsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <PlanningActionsCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function PlanningActionsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const queryClient = useQueryClient()
    const companyId = selectedCompany?.id
    const canRead = canReadPlanning(hasPermission)
    const canManage = canManagePlanning(hasPermission)

    const [statusInput, setStatusInput] = useState("")
    const [status, setStatus] = useState("")
    const [page, setPage] = useState(0)
    const [view, setView] = useState<"list" | "kanban">("list")
    const [formOpen, setFormOpen] = useState(false)
    const [editing, setEditing] = useState<ActionPlanItem | null>(null)
    const [form, setForm] = useState<ActionForm>(EMPTY_FORM)
    const [busy, setBusy] = useState(false)
    const [mutationError, setMutationError] = useState("")

    const query = useQuery({
        queryKey: ["planning-actions", companyId, status, page] as const,
        queryFn: () => listActionPlans(companyId!, status, page, 50),
        enabled: Boolean(companyId && canRead),
        retry: false,
    })

    const goalsQuery = useQuery({
        queryKey: ["planning-action-goals", companyId] as const,
        queryFn: () => listAllActiveGoals(companyId!),
        enabled: Boolean(companyId && canRead),
        retry: false,
    })

    const goalNames = useMemo(
        () =>
            new Map(
                (goalsQuery.data ?? []).map((goal) => [
                    goal.id,
                    goal.name,
                ]),
            ),
        [goalsQuery.data],
    )

    const groups = useMemo(() => {
        const grouped = new Map<string, ActionPlanItem[]>()

        for (const item of query.data?.items ?? []) {
            const key = item.workflowStatus
            grouped.set(key, [...(grouped.get(key) ?? []), item])
        }

        return [...grouped.entries()]
    }, [query.data])

    if (!selectedCompany) {
        return (
            <Panel
                title="Plano de ação"
                help="Ações 5W2H da empresa selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para consultar o plano de ação.
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

    function openEdit(item: ActionPlanItem) {
        setEditing(item)
        setForm({
            goalId: item.goalId ?? "",
            what: item.what,
            why: item.why ?? "",
            responsible: item.responsible,
            dueOn: item.dueOn ?? "",
            where: item.where ?? "",
            how: item.how ?? "",
            cost: item.cost ?? "",
            workflowStatus: item.workflowStatus,
        })
        setMutationError("")
        setFormOpen(true)
    }

    function payload() {
        return {
            goalId: form.goalId || null,
            what: form.what.trim(),
            why: form.why.trim() || null,
            responsible: form.responsible.trim(),
            dueOn: form.dueOn || null,
            where: form.where.trim() || null,
            how: form.how.trim() || null,
            cost: form.cost || null,
            workflowStatus: form.workflowStatus.trim(),
        }
    }

    async function save() {
        if (!companyId) return

        setBusy(true)
        setMutationError("")

        try {
            if (editing) {
                await updateActionPlan(
                    companyId,
                    editing,
                    payload(),
                )
            } else {
                await createActionPlan(companyId, payload())
            }

            await queryClient.invalidateQueries({
                queryKey: ["planning-actions", companyId],
            })
            setFormOpen(false)
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    async function toggleCompleted(item: ActionPlanItem) {
        if (!companyId) return

        setBusy(true)
        setMutationError("")

        try {
            await setActionCompleted(
                companyId,
                item,
                item.completedAt === null,
            )
            await queryClient.invalidateQueries({
                queryKey: ["planning-actions", companyId],
            })
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    async function remove(item: ActionPlanItem) {
        if (
            !companyId ||
            !window.confirm(
                `Excluir a ação "${item.what}"?`,
            )
        ) {
            return
        }

        setBusy(true)
        setMutationError("")

        try {
            await deleteActionPlan(
                companyId,
                item.id,
                item.version,
            )
            const refreshed = await query.refetch()
            const nextPage = reconcilePlanningPage(
                page,
                refreshed.data?.totalPages ?? 0,
                refreshed.data?.items.length ?? 0,
            )

            await queryClient.invalidateQueries({
                queryKey: ["planning-actions", companyId],
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

    function actionButtons(item: ActionPlanItem) {
        if (!canManage) return null

        return (
            <div className="tools">
                <button
                    type="button"
                    className="btn sec sm"
                    onClick={() => openEdit(item)}
                >
                    Editar
                </button>
                <button
                    type="button"
                    className={
                        item.completedAt
                            ? "btn sec sm"
                            : "btn ok sm"
                    }
                    disabled={busy}
                    onClick={() => void toggleCompleted(item)}
                >
                    {item.completedAt ? "Reabrir" : "Concluir"}
                </button>
                <button
                    type="button"
                    className="btn danger sm"
                    disabled={busy}
                    onClick={() => void remove(item)}
                >
                    Excluir
                </button>
            </div>
        )
    }

    const result = query.data

    return (
        <>
            <Panel
                title="Plano de ação"
                help="Ações 5W2H vinculadas opcionalmente às metas do planejamento."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="actionStatusFilter">
                            Status
                        </label>
                        <input
                            id="actionStatusFilter"
                            type="search"
                            value={statusInput}
                            placeholder="Todos os status"
                            onChange={(event) =>
                                setStatusInput(event.target.value)
                            }
                        />
                    </div>

                    <button
                        type="button"
                        className="btn sec"
                        onClick={() => {
                            setPage(0)
                            setStatus(statusInput.trim())
                        }}
                    >
                        Filtrar
                    </button>

                    <div className="seg">
                        <button
                            type="button"
                            className={view === "list" ? "on e" : ""}
                            onClick={() => setView("list")}
                        >
                            Lista
                        </button>
                        <button
                            type="button"
                            className={view === "kanban" ? "on e" : ""}
                            onClick={() => setView("kanban")}
                        >
                            Kanban
                        </button>
                    </div>

                    <span style={{ flex: 1 }} />

                    {canManage ? (
                        <button
                            type="button"
                            className="btn"
                            onClick={openCreate}
                        >
                            Nova ação
                        </button>
                    ) : null}
                </div>

                <p className="help">
                    As colunas do kanban refletem os status persistidos; a
                    primeira entrega não impõe um workflow que não esteja
                    homologado.
                </p>

                {mutationError ? (
                    <div className="alert e">{mutationError}</div>
                ) : null}
            </Panel>

            {query.isError ? (
                <QueryError
                    error={query.error}
                    messages={{
                        403: "Seu acesso não permite consultar o plano de ação.",
                    }}
                    fallback="Não foi possível carregar o plano de ação."
                    onRetry={() => query.refetch()}
                />
            ) : null}

            {query.isPending ? (
                <div className="empty">Carregando ações…</div>
            ) : null}

            {!query.isPending &&
            !query.isError &&
            result?.items.length === 0 ? (
                <div className="empty">
                    Nenhuma ação encontrada.
                </div>
            ) : null}

            {result && result.items.length > 0 && view === "list" ? (
                <Panel>
                    <div className="tblwrap">
                        <table>
                            <thead>
                                <tr>
                                    <th>Ação</th>
                                    <th>Status</th>
                                    <th>Responsável</th>
                                    <th>Prazo</th>
                                    <th>Meta</th>
                                    <th className="n">Custo</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {result.items.map((item) => (
                                    <tr key={item.id}>
                                        <td>
                                            <b>{item.what}</b>
                                            {item.why ? (
                                                <div className="mut">
                                                    {item.why}
                                                </div>
                                            ) : null}
                                        </td>
                                        <td>
                                            <span
                                                className={
                                                    item.completedAt
                                                        ? "badge b-ok"
                                                        : "badge b-gold"
                                                }
                                            >
                                                {item.workflowStatus}
                                            </span>
                                        </td>
                                        <td>{item.responsible}</td>
                                        <td>{item.dueOn ?? "—"}</td>
                                        <td>
                                            {item.goalId
                                                ? goalNames.get(
                                                      item.goalId,
                                                  ) ?? "Meta"
                                                : "—"}
                                        </td>
                                        <td className="n">
                                            {item.cost
                                                ? formatExactCurrency(
                                                      item.cost,
                                                  )
                                                : "—"}
                                        </td>
                                        {canManage ? (
                                            <td>
                                                {actionButtons(item)}
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </Panel>
            ) : null}

            {result && result.items.length > 0 && view === "kanban" ? (
                <div className="kb">
                    {groups.map(([groupStatus, items]) => (
                        <div
                            className="kb-col"
                            key={groupStatus}
                        >
                            <div className="kb-h">
                                <span>{groupStatus}</span>
                                <span>{items.length}</span>
                            </div>

                            {items.map((item) => (
                                <div
                                    className="kb-card"
                                    key={item.id}
                                >
                                    <b>{item.what}</b>
                                    <div className="mut">
                                        {item.responsible}
                                        {item.dueOn
                                            ? ` · ${item.dueOn}`
                                            : ""}
                                    </div>
                                    {item.goalId ? (
                                        <div className="help">
                                            {goalNames.get(item.goalId) ??
                                                "Meta vinculada"}
                                        </div>
                                    ) : null}
                                    {item.completedAt ? (
                                        <div
                                            className="badge b-ok"
                                            style={{ marginTop: 8 }}
                                        >
                                            Concluída
                                        </div>
                                    ) : null}
                                    {canManage ? (
                                        <div className="kb-mv">
                                            <button
                                                type="button"
                                                className="btn sec sm"
                                                onClick={() =>
                                                    openEdit(item)
                                                }
                                            >
                                                Editar
                                            </button>
                                            <button
                                                type="button"
                                                className="btn sm"
                                                disabled={busy}
                                                onClick={() =>
                                                    void toggleCompleted(
                                                        item,
                                                    )
                                                }
                                            >
                                                {item.completedAt
                                                    ? "Reabrir"
                                                    : "Concluir"}
                                            </button>
                                        </div>
                                    ) : null}
                                </div>
                            ))}
                        </div>
                    ))}
                </div>
            ) : null}

            {result && result.totalPages > 1 ? (
                <Panel>
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
                </Panel>
            ) : null}

            {formOpen ? (
                <Modal onClose={busy ? undefined : () => setFormOpen(false)} labelledBy="actionDialogTitle">
                        <h2 id="actionDialogTitle">
                            {editing ? "Editar ação" : "Nova ação"}
                        </h2>

                        <div className="fg">
                            <div className="fl full">
                                <label htmlFor="actionWhat">
                                    O quê
                                </label>
                                <input
                                    id="actionWhat"
                                    type="text"
                                    value={form.what}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            what: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="actionWhy">
                                    Por quê
                                </label>
                                <textarea
                                    id="actionWhy"
                                    value={form.why}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            why: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="actionResponsible">
                                    Responsável
                                </label>
                                <input
                                    id="actionResponsible"
                                    type="text"
                                    value={form.responsible}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            responsible:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="actionDueOn">
                                    Prazo
                                </label>
                                <input
                                    id="actionDueOn"
                                    type="date"
                                    value={form.dueOn}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            dueOn: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="actionStatus">
                                    Status
                                </label>
                                <input
                                    id="actionStatus"
                                    type="text"
                                    value={form.workflowStatus}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            workflowStatus:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="actionGoal">
                                    Meta
                                </label>
                                <select
                                    id="actionGoal"
                                    value={form.goalId}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            goalId: event.target.value,
                                        }))
                                    }
                                >
                                    <option value="">
                                        Sem vínculo
                                    </option>
                                    {goalsQuery.data?.map(
                                        (goal) => (
                                            <option
                                                key={goal.id}
                                                value={goal.id}
                                            >
                                                {goal.name}
                                            </option>
                                        ),
                                    )}
                                </select>
                            </div>

                            <div className="fl">
                                <label htmlFor="actionWhere">
                                    Onde
                                </label>
                                <input
                                    id="actionWhere"
                                    type="text"
                                    value={form.where}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            where: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="actionCost">
                                    Quanto
                                </label>
                                <input
                                    id="actionCost"
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    value={form.cost}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            cost: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="actionHow">
                                    Como
                                </label>
                                <textarea
                                    id="actionHow"
                                    value={form.how}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            how: event.target.value,
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
                                    !form.what.trim() ||
                                    !form.responsible.trim() ||
                                    !form.workflowStatus.trim()
                                }
                                onClick={() => void save()}
                            >
                                {busy ? "Salvando…" : "Salvar"}
                            </button>
                        </div>
                </Modal>
            ) : null}
        </>
    )
}
