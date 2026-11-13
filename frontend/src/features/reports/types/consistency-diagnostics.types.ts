export type ConsistencyCheckStatus = "OK" | "ISSUE"

export interface ConsistencyDiagnosticCheck {
    code: string
    label: string
    status: ConsistencyCheckStatus
    occurrenceCount: number
    occurrenceIds: string[]
}

export interface ConsistencyDiagnosticsReport {
    companyId: string
    partial: boolean
    coverage: string[]
    issueCount: number
    checks: ConsistencyDiagnosticCheck[]
}
