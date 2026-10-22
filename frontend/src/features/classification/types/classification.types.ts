export type ClassificationStatus =
    | "PENDING"
    | "CLASSIFIED"

export interface AccountingAccountClassification {
    accountId: string
    accountingSourceId: string
    accountKey: string
    description: string
    status: ClassificationStatus
    classificationId: string | null
    dreClassification: string | null
    dreOpenClassification: string | null
    ebitdaClassification: string | null
}

export interface DreClassification {
    id: string
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
}