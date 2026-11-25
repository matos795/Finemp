import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { useCompanyAccess } from "../../companies/company-access-context"
import {
    createJobPosition,
    listJobPositions,
    updateJobPosition,
} from "../api/people-api"
import type {
    JobPosition,
    JobPositionFilter,
    JobPositionValues,
} from "../types/people.types"

type DialogState = { position?: JobPosition } | null

/**
 * Cargos e descrições (DRC), primeira fatia (#462): cadastro operacional do
 * cargo com descrição livre. Sem faixa salarial, competências ou avaliação.
 */
export function JobPositionsPage() {
    const { selectedCompany } = useCompanyAccess()
    return <JobPositionsCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function JobPositionsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = Boolean(companyId) && hasPermission("EMPLOYEE_MANAGE")
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<JobPositionFilter>({ search: "", active: "true" })
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const positions = useQuery({
        queryKey: ["job-positions", companyId, filter, page] as const,
        queryFn: ({ signal }) => listJobPositions(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    useEffect(() => {
        if (searchText === filter.search) return
        const timer = window.setTimeout(
            () => setFilter((current) => ({ ...current, search: searchText })),
            300,
        )
        return () => window.clearTimeout(timer)
    }, [searchText, filter.search])

    useEffect(() => {
        const totalPages = positions.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [positions.data?.totalPages, page, setPage])

    const items = positions.data?.items ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="positionActive">Situação</label>
                    <select
                        id="positionActive"
                        value={filter.active}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({
                                ...filter,
                                active: event.target.value as JobPositionFilter["active"],
                            })
                        }}
                    >
                        <option value="true">Ativos</option>
                        <option value="false">Inativos</option>
                        <option value="">Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="positionSearch">Buscar</label>
                    <input
                        id="positionSearch"
                        type="search"
                        maxLength={120}
                        placeholder="Nome do cargo…"
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
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
                            + Cargo
                        </button>
                    </div>
                ) : null}
            </div>

            {positions.isError ? (
                <QueryError
                    error={positions.error}
                    messages={{
                        400: "Confira os filtros informados.",
                        403: "Você não tem acesso à gestão de pessoas desta empresa.",
                    }}
                    fallback="Não foi possível carregar os cargos."
                    onRetry={() => positions.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={positions.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Cargo</th>
                                    <th>Descrição (DRC)</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((position) => (
                                    <tr key={position.id}>
                                        <td><b>{position.title}</b></td>
                                        <td>
                                            <div className="sub" style={{ whiteSpace: "pre-line" }}>
                                                {position.description || "—"}
                                            </div>
                                        </td>
                                        <td>
                                            <span className={position.active ? "badge b-ok" : "badge b-neu"}>
                                                {position.active ? "Ativo" : "Inativo"}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    onClick={() => setDialog({ position })}
                                                >
                                                    Editar
                                                </button>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {positions.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            {companyId
                                ? "Nenhum cargo encontrado."
                                : "Escolha uma empresa para ver os cargos."}
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={positions.data?.totalPages ?? 0}
                        disabled={positions.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog && companyId ? (
                <JobPositionDialog
                    key={dialog.position ? dialog.position.id + "-" + dialog.position.version : "new"}
                    companyId={companyId}
                    position={dialog.position}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({ queryKey: ["job-positions", companyId] })
                        await queryClient.invalidateQueries({ queryKey: ["employees", companyId] })
                        showToast(dialog.position ? "Cargo atualizado." : "Cargo cadastrado.")
                        setDialog(null)
                    }}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}

function JobPositionDialog({
    companyId,
    position,
    onSaved,
    onClose,
}: {
    companyId: string
    position?: JobPosition
    onSaved: () => Promise<void>
    onClose: () => void
}) {
    const queryClient = useQueryClient()
    const [title, setTitle] = useState(position?.title ?? "")
    const [description, setDescription] = useState(position?.description ?? "")
    const [active, setActive] = useState(position?.active ?? true)
    const [error, setError] = useState("")

    const save = useMutation({
        mutationFn: async () => {
            setError("")
            if (!title.trim()) throw new Error("VALIDATION")

            const values: JobPositionValues = {
                title: title.trim(),
                description: description.trim() || null,
                active,
            }

            return position
                ? updateJobPosition(companyId, position, values)
                : createJobPosition(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: async (caught) => {
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Informe o nome do cargo.")
                return
            }
            if (caught instanceof ApiError && caught.code === "PEOPLE_DUPLICATE_POSITION") {
                setError("Já existe um cargo com este nome nesta empresa.")
                return
            }
            const statusCode = caught instanceof ApiError ? caught.status : undefined
            if (statusCode === 409) {
                await queryClient.invalidateQueries({ queryKey: ["job-positions", companyId] })
                setError("O cargo foi alterado por outro usuário. Feche e abra novamente.")
            } else if (statusCode === 403) {
                setError("Seu acesso não permite alterar cargos.")
            } else {
                setError("Não foi possível salvar o cargo.")
            }
        },
    })

    return (
        <Modal
            onClose={save.isPending ? undefined : onClose}
            labelledBy="positionDialogTitle"
        >
            <h2 id="positionDialogTitle">{position ? "Editar cargo" : "Novo cargo"}</h2>

            <div className="fg">
                <div className="fl full">
                    <label htmlFor="positionTitle">Cargo</label>
                    <input
                        id="positionTitle"
                        type="text"
                        maxLength={120}
                        value={title}
                        onChange={(event) => setTitle(event.target.value)}
                    />
                </div>

                <div className="fl full">
                    <label htmlFor="positionDescription">Descrição do cargo (DRC)</label>
                    <textarea
                        id="positionDescription"
                        maxLength={4000}
                        rows={6}
                        value={description}
                        placeholder="Objetivo, responsabilidades e requisitos do cargo."
                        onChange={(event) => setDescription(event.target.value)}
                    />
                </div>

                <div className="fl full">
                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={active}
                            onChange={(event) => setActive(event.target.checked)}
                        />
                        Cargo ativo (inativos continuam nos funcionários que já os têm)
                    </label>
                </div>
            </div>

            {error ? <div className="alert e">{error}</div> : null}

            <div className="mact">
                <button type="button" className="btn sec" disabled={save.isPending} onClick={onClose}>
                    Cancelar
                </button>
                <button type="button" className="btn" disabled={save.isPending} onClick={() => save.mutate()}>
                    {save.isPending ? "Salvando…" : "Salvar"}
                </button>
            </div>
        </Modal>
    )
}
