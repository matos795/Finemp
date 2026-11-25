import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { ApiError } from "../../../lib/api/api-client"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { formatIsoDate } from "../../../lib/formatters/decimal"
import { useCompanyAccess } from "../../companies/company-access-context"
import { listAllResultCenters } from "../../result-centers/api/result-center-api"
import {
    createEmployee,
    listAllJobPositions,
    listEmployees,
    updateEmployee,
} from "../api/people-api"
import {
    employeeStatusLabel,
    employmentTypeOptions,
    type Employee,
    type EmployeeFilter,
    type EmployeeStatus,
    type EmployeeValues,
    type EmploymentType,
} from "../types/people.types"

const typeLabel = Object.fromEntries(
    employmentTypeOptions.map((option) => [option.value, option.label]),
) as Record<EmploymentType, string>

type DialogState = { employee?: Employee } | null

/**
 * Funcionários, primeira fatia (#462): cadastro operacional mínimo.
 * Sem documento, nascimento, salário ou contato; custo e regras trabalhistas
 * continuam no roadmap (#45).
 */
export function EmployeesPage() {
    const { selectedCompany } = useCompanyAccess()
    return <EmployeesCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function EmployeesCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = Boolean(companyId) && hasPermission("EMPLOYEE_MANAGE")
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<EmployeeFilter>({
        search: "",
        employmentType: "",
        status: "ACTIVE",
        jobPositionId: "",
        resultCenterId: "",
    })
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const employees = useQuery({
        queryKey: ["employees", companyId, filter, page] as const,
        queryFn: ({ signal }) => listEmployees(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const positions = useQuery({
        queryKey: ["job-positions", companyId, "options"] as const,
        queryFn: () => listAllJobPositions(companyId!, ""),
        enabled: Boolean(companyId),
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
        const totalPages = employees.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [employees.data?.totalPages, page, setPage])

    const items = employees.data?.items ?? []
    const positionItems = positions.data ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="employeeStatus">Situação</label>
                    <select
                        id="employeeStatus"
                        value={filter.status}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({ ...filter, status: event.target.value as EmployeeStatus | "" })
                        }}
                    >
                        <option value="ACTIVE">Ativos</option>
                        <option value="TERMINATED">Desligados</option>
                        <option value="">Todos</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="employeeType">Regime</label>
                    <select
                        id="employeeType"
                        value={filter.employmentType}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({ ...filter, employmentType: event.target.value as EmploymentType | "" })
                        }}
                    >
                        <option value="">Todos</option>
                        {employmentTypeOptions.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="employeePosition">Cargo</label>
                    <select
                        id="employeePosition"
                        value={filter.jobPositionId}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({ ...filter, jobPositionId: event.target.value })
                        }}
                    >
                        <option value="">Todos</option>
                        {positionItems.map((position) => (
                            <option key={position.id} value={position.id}>{position.title}</option>
                        ))}
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="employeeSearch">Buscar</label>
                    <input
                        id="employeeSearch"
                        type="search"
                        maxLength={120}
                        placeholder="Nome ou cargo…"
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
                            + Funcionário
                        </button>
                    </div>
                ) : null}
            </div>

            {employees.isError ? (
                <QueryError
                    error={employees.error}
                    messages={{
                        400: "Confira os filtros informados.",
                        403: "Você não tem acesso à gestão de pessoas desta empresa.",
                    }}
                    fallback="Não foi possível carregar os funcionários."
                    onRetry={() => employees.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={employees.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Nome</th>
                                    <th>Cargo</th>
                                    <th>Regime</th>
                                    <th>Centro de resultado</th>
                                    <th>Admissão</th>
                                    <th>Desligamento</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((employee) => (
                                    <tr key={employee.id}>
                                        <td><b>{employee.fullName}</b></td>
                                        <td>{employee.jobPositionTitle || "—"}</td>
                                        <td>{typeLabel[employee.employmentType]}</td>
                                        <td>{employee.resultCenterName || "—"}</td>
                                        <td>{formatIsoDate(employee.hiredOn)}</td>
                                        <td>{employee.terminatedOn ? formatIsoDate(employee.terminatedOn) : "—"}</td>
                                        <td>
                                            <span className={employee.status === "ACTIVE" ? "badge b-ok" : "badge b-neu"}>
                                                {employeeStatusLabel[employee.status]}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    onClick={() => setDialog({ employee })}
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

                    {employees.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            {companyId
                                ? "Nenhum funcionário encontrado."
                                : "Escolha uma empresa para ver os funcionários."}
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={employees.data?.totalPages ?? 0}
                        disabled={employees.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog && companyId ? (
                <EmployeeDialog
                    key={dialog.employee ? dialog.employee.id + "-" + dialog.employee.version : "new"}
                    companyId={companyId}
                    employee={dialog.employee}
                    onSaved={async () => {
                        await queryClient.invalidateQueries({ queryKey: ["employees", companyId] })
                        showToast(dialog.employee ? "Funcionário atualizado." : "Funcionário cadastrado.")
                        setDialog(null)
                    }}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}

function EmployeeDialog({
    companyId,
    employee,
    onSaved,
    onClose,
}: {
    companyId: string
    employee?: Employee
    onSaved: () => Promise<void>
    onClose: () => void
}) {
    const queryClient = useQueryClient()
    const [fullName, setFullName] = useState(employee?.fullName ?? "")
    const [employmentType, setEmploymentType] = useState<EmploymentType>(employee?.employmentType ?? "CLT")
    const [jobPositionId, setJobPositionId] = useState(employee?.jobPositionId ?? "")
    const [resultCenterId, setResultCenterId] = useState(employee?.resultCenterId ?? "")
    const [hiredOn, setHiredOn] = useState(employee?.hiredOn ?? "")
    const [terminatedOn, setTerminatedOn] = useState(employee?.terminatedOn ?? "")
    const [error, setError] = useState("")

    const positions = useQuery({
        queryKey: ["job-positions", companyId, "dialog-options"] as const,
        queryFn: () => listAllJobPositions(companyId!, "true"),
        retry: false,
    })

    const centers = useQuery({
        queryKey: ["employee-centers", companyId] as const,
        queryFn: () => listAllResultCenters(companyId),
        retry: false,
    })

    const save = useMutation({
        mutationFn: async () => {
            setError("")
            if (!fullName.trim() || !hiredOn || terminatedOn && terminatedOn < hiredOn) {
                throw new Error("VALIDATION")
            }

            const values: EmployeeValues = {
                fullName: fullName.trim(),
                employmentType,
                jobPositionId: jobPositionId || null,
                resultCenterId: resultCenterId || null,
                hiredOn,
                terminatedOn: terminatedOn || null,
            }

            return employee
                ? updateEmployee(companyId, employee, values)
                : createEmployee(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: async (caught) => {
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Informe nome e admissão; o desligamento não pode ser anterior à admissão.")
                return
            }
            const statusCode = caught instanceof ApiError ? caught.status : undefined
            if (statusCode === 409) {
                await queryClient.invalidateQueries({ queryKey: ["employees", companyId] })
                setError("O cadastro foi alterado por outro usuário. Feche e abra novamente.")
            } else if (statusCode === 403) {
                setError("Seu acesso não permite alterar funcionários.")
            } else if (statusCode === 400) {
                setError("Confira os dados; cargos inativos não podem ser atribuídos.")
            } else if (statusCode === 404) {
                setError("O cargo ou centro escolhido não existe mais nesta empresa.")
            } else {
                setError("Não foi possível salvar o funcionário.")
            }
        },
    })

    // Cargo inativo continua aparecendo para quem já o tem.
    const positionItems = positions.data ?? []
    const keepsCurrentPosition = employee?.jobPositionId
        && !positionItems.some((position) => position.id === employee.jobPositionId)
    const centerItems = centers.data?.filter((center) => center.active || center.id === employee?.resultCenterId) ?? []

    return (
        <Modal
            onClose={save.isPending ? undefined : onClose}
            labelledBy="employeeDialogTitle"
        >
            <h2 id="employeeDialogTitle">{employee ? "Editar funcionário" : "Novo funcionário"}</h2>

            <div className="fg">
                <div className="fl full">
                    <label htmlFor="employeeName">Nome</label>
                    <input
                        id="employeeName"
                        type="text"
                        maxLength={160}
                        value={fullName}
                        onChange={(event) => setFullName(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="employeeTypeInput">Regime</label>
                    <select
                        id="employeeTypeInput"
                        value={employmentType}
                        onChange={(event) => setEmploymentType(event.target.value as EmploymentType)}
                    >
                        {employmentTypeOptions.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="employeePositionInput">Cargo</label>
                    <select
                        id="employeePositionInput"
                        value={jobPositionId}
                        onChange={(event) => setJobPositionId(event.target.value)}
                    >
                        <option value="">Sem cargo</option>
                        {keepsCurrentPosition ? (
                            <option value={employee!.jobPositionId!}>
                                {employee!.jobPositionTitle} (inativo)
                            </option>
                        ) : null}
                        {positionItems.map((position) => (
                            <option key={position.id} value={position.id}>{position.title}</option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="employeeCenterInput">Centro de resultado</label>
                    <select
                        id="employeeCenterInput"
                        value={resultCenterId}
                        onChange={(event) => setResultCenterId(event.target.value)}
                    >
                        <option value="">Sem centro</option>
                        {centerItems.map((center) => (
                            <option key={center.id} value={center.id}>{center.name}</option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="employeeHired">Admissão</label>
                    <input
                        id="employeeHired"
                        type="date"
                        value={hiredOn}
                        onChange={(event) => setHiredOn(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="employeeTerminated">Desligamento (opcional)</label>
                    <input
                        id="employeeTerminated"
                        type="date"
                        value={terminatedOn}
                        onChange={(event) => setTerminatedOn(event.target.value)}
                    />
                </div>
            </div>

            {positions.isError ? (
                <div className="alert w">Não foi possível consultar os cargos; o funcionário pode ser salvo sem cargo.</div>
            ) : null}
            {centers.isError ? (
                <div className="alert w">Não foi possível consultar centros de resultado; o funcionário pode ser salvo sem centro.</div>
            ) : null}

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
