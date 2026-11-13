import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Panel } from "../../../components/finemp/panel"
import { QueryError } from "../../../components/finemp/query-error"
import { todayIso } from "../../../lib/formatters/local-date"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createGoal,
    deactivateGoal,
    listGoalMeasurements,
    listGoals,
    measureGoal,
    updateGoal,
    type Goal,
} from "../api/planning-api.ts"
import {
    canManagePlanning,
    canReadPlanning,
    describePlanningMutationError,
    reconcilePlanningPage,
} from "../lib/planning-ui.ts"

interface GoalForm {
    name: string
    direction: "UP" | "DOWN"
    baselineValue: string
    targetValue: string
    dueOn: string
    notes: string
}

const EMPTY_GOAL: GoalForm = {
    name: "",
    direction: "UP",
    baselineValue: "",
    targetValue: "",
    dueOn: "",
    notes: "",
}

function progressLabel(progress: string | null) {
    if (progress === null) return "—"
    const value = Number(progress)
    if (!Number.isFinite(value)) return progress
    return new Intl.NumberFormat("pt-BR", {
        style: "percent",
        minimumFractionDigits: 0,
        maximumFractionDigits: 1,
    }).format(value)
}

function progressWidth(progress: string | null) {
    if (progress === null) return 0
    const value = Number(progress)
    if (!Number.isFinite(value)) return 0
    return Math.max(0, Math.min(100, value * 100))
}

function GoalStatusBadge({ goal }: { goal: Goal }) {
    if (!goal.active) {
        return <span className="badge b-neu">Inativa</span>
    }

    if (goal.achieved === true) {
        return <span className="badge b-ok">Atingida</span>
    }

    if (goal.currentValue === null) {
        return <span className="badge b-neu">Sem medição</span>
    }

    return <span className="badge b-gold">Em acompanhamento</span>
}

export function PlanningGoalsPage() {
    const { selectedCompany } = useCompanyAccess()

    return (
        <PlanningGoalsCompanyPage
            key={selectedCompany?.id ?? "no-company"}
        />
    )
}

function PlanningGoalsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const queryClient = useQueryClient()
    const companyId = selectedCompany?.id
    const canRead = canReadPlanning(hasPermission)
    const canManage = canManagePlanning(hasPermission)

    const [activeFilter, setActiveFilter] = useState("active")
    const [page, setPage] = useState(0)
    const [formOpen, setFormOpen] = useState(false)
    const [editing, setEditing] = useState<Goal | null>(null)
    const [form, setForm] = useState<GoalForm>(EMPTY_GOAL)
    const [measureTarget, setMeasureTarget] = useState<Goal | null>(null)
    const [measurement, setMeasurement] = useState({
        measuredOn: todayIso(),
        measuredValue: "",
        notes: "",
    })
    const [historyTarget, setHistoryTarget] = useState<Goal | null>(null)
    const [historyPage, setHistoryPage] = useState(0)
    const [busy, setBusy] = useState(false)
    const [mutationError, setMutationError] = useState("")

    const active =
        activeFilter === "all"
            ? null
            : activeFilter === "active"

    const query = useQuery({
        queryKey: ["planning-goals", companyId, activeFilter, page] as const,
        queryFn: () => listGoals(companyId!, active, page, 50),
        enabled: Boolean(companyId && canRead),
        retry: false,
    })

    const historyQuery = useQuery({
        queryKey: [
            "planning-goal-measurements",
            companyId,
            historyTarget?.id,
            historyPage,
        ] as const,
        queryFn: () =>
            listGoalMeasurements(
                companyId!,
                historyTarget!.id,
                historyPage,
                50,
            ),
        enabled: Boolean(companyId && historyTarget && canRead),
        retry: false,
    })

    if (!selectedCompany) {
        return (
            <Panel
                title="Metas e indicadores"
                help="Objetivos mensuráveis da empresa selecionada."
            >
                <div className="alert i">
                    Selecione uma empresa para consultar as metas.
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
        setForm(EMPTY_GOAL)
        setMutationError("")
        setFormOpen(true)
    }

    function openEdit(goal: Goal) {
        setEditing(goal)
        setForm({
            name: goal.name,
            direction: goal.direction,
            baselineValue: goal.baselineValue,
            targetValue: goal.targetValue,
            dueOn: goal.dueOn,
            notes: goal.notes ?? "",
        })
        setMutationError("")
        setFormOpen(true)
    }

    async function saveGoal() {
        if (!companyId) return

        setBusy(true)
        setMutationError("")

        const input = {
            name: form.name.trim(),
            direction: form.direction,
            baselineValue: form.baselineValue,
            targetValue: form.targetValue,
            dueOn: form.dueOn,
            notes: form.notes.trim() || null,
        }

        try {
            if (editing) {
                await updateGoal(companyId, editing, input)
            } else {
                await createGoal(companyId, input)
            }

            await queryClient.invalidateQueries({
                queryKey: ["planning-goals", companyId],
            })
            setFormOpen(false)
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    function openHistory(goal: Goal) {
        setHistoryPage(0)
        setHistoryTarget(goal)
    }

    function openMeasure(goal: Goal) {
        setMeasureTarget(goal)
        setMeasurement({
            measuredOn: todayIso(),
            measuredValue: goal.currentValue ?? "",
            notes: "",
        })
        setMutationError("")
    }

    async function saveMeasurement() {
        if (!companyId || !measureTarget) return

        setBusy(true)
        setMutationError("")

        try {
            await measureGoal(companyId, measureTarget.id, {
                measuredOn: measurement.measuredOn,
                measuredValue: measurement.measuredValue,
                notes: measurement.notes.trim() || null,
            })

            await Promise.all([
                queryClient.invalidateQueries({
                    queryKey: ["planning-goals", companyId],
                }),
                queryClient.invalidateQueries({
                    queryKey: [
                        "planning-goal-measurements",
                        companyId,
                        measureTarget.id,
                    ],
                }),
            ])

            setMeasureTarget(null)
        } catch (error) {
            setMutationError(describePlanningMutationError(error))
        } finally {
            setBusy(false)
        }
    }

    async function deactivate(goal: Goal) {
        if (
            !companyId ||
            !window.confirm(
                `Desativar a meta "${goal.name}"? O histórico de medições será preservado.`,
            )
        ) {
            return
        }

        setBusy(true)
        setMutationError("")

        try {
            await deactivateGoal(companyId, goal.id, goal.version)
            const refreshed = await query.refetch()
            const nextPage = reconcilePlanningPage(
                page,
                refreshed.data?.totalPages ?? 0,
                refreshed.data?.items.length ?? 0,
            )

            await queryClient.invalidateQueries({
                queryKey: ["planning-goals", companyId],
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
                title="Metas e indicadores"
                help="Metas com linha de base, alvo, prazo e histórico de medições. O progresso é calculado pelo backend e limitado a 120%."
            >
                <div className="filters">
                    <div className="f">
                        <label htmlFor="goalActiveFilter">
                            Situação
                        </label>
                        <select
                            id="goalActiveFilter"
                            value={activeFilter}
                            onChange={(event) => {
                                setPage(0)
                                setActiveFilter(event.target.value)
                            }}
                        >
                            <option value="active">Ativas</option>
                            <option value="inactive">Inativas</option>
                            <option value="all">Todas</option>
                        </select>
                    </div>

                    <span style={{ flex: 1 }} />

                    {canManage ? (
                        <button
                            type="button"
                            className="btn"
                            onClick={openCreate}
                        >
                            Nova meta
                        </button>
                    ) : null}
                </div>

                <p className="help">
                    A classificação temporal “caminho/risco” será publicada
                    somente quando a data-base homologada estiver explicitada.
                </p>

                {mutationError ? (
                    <div className="alert e">{mutationError}</div>
                ) : null}
            </Panel>

            {query.isError ? (
                <QueryError
                    error={query.error}
                    messages={{
                        403: "Seu acesso não permite consultar metas.",
                    }}
                    fallback="Não foi possível carregar as metas."
                    onRetry={() => query.refetch()}
                />
            ) : null}

            {query.isPending ? (
                <div className="empty">Carregando metas…</div>
            ) : null}

            {!query.isPending &&
            !query.isError &&
            result?.items.length === 0 ? (
                <div className="empty">
                    Nenhuma meta encontrada.
                </div>
            ) : null}

            {result && result.items.length > 0 ? (
                <>
                    <div className="cards">
                        {result.items.map((goal) => (
                            <div className="card" key={goal.id}>
                                <div className="lb">
                                    {goal.direction === "UP"
                                        ? "Meta de crescimento"
                                        : "Meta de redução"}
                                </div>
                                <div className="vl">{goal.name}</div>
                                <div className="sub">
                                    Base {goal.baselineValue} · Alvo{" "}
                                    {goal.targetValue} · Prazo {goal.dueOn}
                                </div>

                                <div style={{ marginTop: 12 }}>
                                    <GoalStatusBadge goal={goal} />
                                </div>

                                <div style={{ marginTop: 12 }}>
                                    <div className="help">
                                        Progresso{" "}
                                        <b>{progressLabel(goal.progress)}</b>
                                    </div>
                                    <div className="prog">
                                        <i
                                            style={{
                                                width: `${progressWidth(
                                                    goal.progress,
                                                )}%`,
                                            }}
                                        />
                                    </div>
                                </div>

                                <div className="sub" style={{ marginTop: 8 }}>
                                    Atual: {goal.currentValue ?? "—"}
                                    {goal.currentMeasuredOn
                                        ? ` · medido em ${goal.currentMeasuredOn}`
                                        : ""}
                                </div>

                                <div className="tools" style={{ marginTop: 12 }}>
                                    <button
                                        type="button"
                                        className="btn sec sm"
                                        onClick={() =>
                                            openHistory(goal)
                                        }
                                    >
                                        Histórico
                                    </button>

                                    {canManage && goal.active ? (
                                        <>
                                            <button
                                                type="button"
                                                className="btn sec sm"
                                                onClick={() =>
                                                    openMeasure(goal)
                                                }
                                            >
                                                Medir
                                            </button>
                                            <button
                                                type="button"
                                                className="btn sec sm"
                                                onClick={() =>
                                                    openEdit(goal)
                                                }
                                            >
                                                Editar
                                            </button>
                                            <button
                                                type="button"
                                                className="btn danger sm"
                                                disabled={busy}
                                                onClick={() =>
                                                    void deactivate(goal)
                                                }
                                            >
                                                Desativar
                                            </button>
                                        </>
                                    ) : null}
                                </div>
                            </div>
                        ))}
                    </div>

                    {result.totalPages > 1 ? (
                        <Panel>
                            <div className="tools">
                                <button
                                    className="btn sec"
                                    type="button"
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
                                <span style={{ flex: 1 }} />
                                <span className="help">
                                    Página {result.page + 1} de{" "}
                                    {result.totalPages}
                                </span>
                                <button
                                    className="btn sec"
                                    type="button"
                                    disabled={
                                        query.isFetching ||
                                        result.page + 1 >=
                                            result.totalPages
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
                </>
            ) : null}

            {formOpen ? (
                <Modal onClose={() => setFormOpen(false)} labelledBy="goalDialogTitle">
                        <h2 id="goalDialogTitle">
                            {editing ? "Editar meta" : "Nova meta"}
                        </h2>

                        <div className="fg">
                            <div className="fl full">
                                <label htmlFor="goalName">Meta</label>
                                <input
                                    id="goalName"
                                    type="text"
                                    value={form.name}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            name: event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="goalDirection">
                                    Sentido
                                </label>
                                <select
                                    id="goalDirection"
                                    value={form.direction}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            direction:
                                                event.target.value as
                                                    | "UP"
                                                    | "DOWN",
                                        }))
                                    }
                                >
                                    <option value="UP">
                                        Aumentar
                                    </option>
                                    <option value="DOWN">
                                        Reduzir
                                    </option>
                                </select>
                            </div>

                            <div className="fl">
                                <label htmlFor="goalDueOn">
                                    Prazo
                                </label>
                                <input
                                    id="goalDueOn"
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
                                <label htmlFor="goalBaseline">
                                    Linha de base
                                </label>
                                <input
                                    id="goalBaseline"
                                    type="number"
                                    step="any"
                                    value={form.baselineValue}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            baselineValue:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="goalTarget">
                                    Alvo
                                </label>
                                <input
                                    id="goalTarget"
                                    type="number"
                                    step="any"
                                    value={form.targetValue}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            targetValue:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="goalNotes">
                                    Observações
                                </label>
                                <textarea
                                    id="goalNotes"
                                    value={form.notes}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            notes: event.target.value,
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
                                    !form.name.trim() ||
                                    !form.baselineValue ||
                                    !form.targetValue ||
                                    !form.dueOn
                                }
                                onClick={() => void saveGoal()}
                            >
                                {busy ? "Salvando…" : "Salvar"}
                            </button>
                        </div>
                </Modal>
            ) : null}

            {measureTarget ? (
                <Modal onClose={() => setMeasureTarget(null)} labelledBy="measureGoalDialogTitle">
                        <h2 id="measureGoalDialogTitle">
                            Medir · {measureTarget.name}
                        </h2>

                        <div className="fg">
                            <div className="fl">
                                <label htmlFor="measurementDate">
                                    Data
                                </label>
                                <input
                                    id="measurementDate"
                                    type="date"
                                    value={measurement.measuredOn}
                                    onChange={(event) =>
                                        setMeasurement((current) => ({
                                            ...current,
                                            measuredOn:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl">
                                <label htmlFor="measurementValue">
                                    Valor
                                </label>
                                <input
                                    id="measurementValue"
                                    type="number"
                                    step="any"
                                    value={measurement.measuredValue}
                                    onChange={(event) =>
                                        setMeasurement((current) => ({
                                            ...current,
                                            measuredValue:
                                                event.target.value,
                                        }))
                                    }
                                />
                            </div>

                            <div className="fl full">
                                <label htmlFor="measurementNotes">
                                    Observações
                                </label>
                                <textarea
                                    id="measurementNotes"
                                    value={measurement.notes}
                                    onChange={(event) =>
                                        setMeasurement((current) => ({
                                            ...current,
                                            notes: event.target.value,
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
                                onClick={() => setMeasureTarget(null)}
                            >
                                Cancelar
                            </button>
                            <button
                                type="button"
                                className="btn"
                                disabled={
                                    busy ||
                                    !measurement.measuredOn ||
                                    !measurement.measuredValue
                                }
                                onClick={() => void saveMeasurement()}
                            >
                                {busy ? "Salvando…" : "Registrar medição"}
                            </button>
                        </div>
                </Modal>
            ) : null}

            {historyTarget ? (
                <Modal onClose={() => setHistoryTarget(null)} labelledBy="goalHistoryDialogTitle">
                        <h2 id="goalHistoryDialogTitle">
                            Histórico · {historyTarget.name}
                        </h2>

                        {historyQuery.isPending ? (
                            <div className="empty">
                                Carregando medições…
                            </div>
                        ) : null}

                        {historyQuery.isError ? (
                            <QueryError
                                error={historyQuery.error}
                                fallback="Não foi possível carregar as medições."
                                onRetry={() => historyQuery.refetch()}
                            />
                        ) : null}

                        {historyQuery.data ? (
                            <div className="tblwrap">
                                <table>
                                    <thead>
                                        <tr>
                                            <th>Data</th>
                                            <th className="n">Valor</th>
                                            <th>Observação</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {historyQuery.data.items.map(
                                            (item) => (
                                                <tr key={item.id}>
                                                    <td>
                                                        {item.measuredOn}
                                                    </td>
                                                    <td className="n">
                                                        {item.measuredValue}
                                                    </td>
                                                    <td>
                                                        {item.notes ?? "—"}
                                                    </td>
                                                </tr>
                                            ),
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        ) : null}

                        {historyQuery.data &&
                        historyQuery.data.totalPages > 1 ? (
                            <div className="tools">
                                <button
                                    type="button"
                                    className="btn sec"
                                    disabled={
                                        historyQuery.isFetching ||
                                        historyQuery.data.page === 0
                                    }
                                    onClick={() =>
                                        setHistoryPage((current) =>
                                            Math.max(0, current - 1),
                                        )
                                    }
                                >
                                    Anterior
                                </button>
                                <span style={{ flex: 1 }} />
                                <span className="help">
                                    Página {historyQuery.data.page + 1} de{" "}
                                    {historyQuery.data.totalPages}
                                </span>
                                <button
                                    type="button"
                                    className="btn sec"
                                    disabled={
                                        historyQuery.isFetching ||
                                        historyQuery.data.page + 1 >=
                                            historyQuery.data.totalPages
                                    }
                                    onClick={() =>
                                        setHistoryPage(
                                            (current) => current + 1,
                                        )
                                    }
                                >
                                    Próxima
                                </button>
                            </div>
                        ) : null}

                        <div className="mact">
                            <button
                                type="button"
                                className="btn sec"
                                onClick={() => setHistoryTarget(null)}
                            >
                                Fechar
                            </button>
                        </div>
                </Modal>
            ) : null}
        </>
    )
}
