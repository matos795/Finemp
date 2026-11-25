import type { PageResponse } from "../../../lib/api/pagination.types.ts"

/** Regimes do protótipo homologado (F20). Só código e rótulo; sem regra de custo. */
export type EmploymentType = "CLT" | "APR" | "PRO" | "EST" | "PJ"

export type EmployeeStatus = "ACTIVE" | "TERMINATED"

export const employmentTypeOptions: Array<{ value: EmploymentType, label: string }> = [
    { value: "CLT", label: "CLT" },
    { value: "APR", label: "Aprendiz" },
    { value: "PRO", label: "Pró-labore" },
    { value: "EST", label: "Estágio" },
    { value: "PJ", label: "PJ" },
]

export const employeeStatusLabel: Record<EmployeeStatus, string> = {
    ACTIVE: "Ativo",
    TERMINATED: "Desligado",
}

export interface JobPosition {
    id: string
    title: string
    description: string | null
    active: boolean
    version: number
}

export interface JobPositionValues {
    title: string
    description: string | null
    active: boolean
}

export interface JobPositionFilter {
    search: string
    active: "" | "true" | "false"
}

export type JobPositionPage = PageResponse<JobPosition>

/** Cadastro operacional mínimo: sem documento, nascimento, salário ou contato. */
export interface Employee {
    id: string
    fullName: string
    employmentType: EmploymentType
    jobPositionId: string | null
    jobPositionTitle: string | null
    resultCenterId: string | null
    resultCenterName: string | null
    hiredOn: string
    terminatedOn: string | null
    status: EmployeeStatus
    version: number
}

export interface EmployeeValues {
    fullName: string
    employmentType: EmploymentType
    jobPositionId: string | null
    resultCenterId: string | null
    hiredOn: string
    terminatedOn: string | null
}

export interface EmployeeFilter {
    search: string
    employmentType: EmploymentType | ""
    status: EmployeeStatus | ""
    jobPositionId: string
    resultCenterId: string
}

export type EmployeePage = PageResponse<Employee>
