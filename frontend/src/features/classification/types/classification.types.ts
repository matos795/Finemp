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

export interface AccountingAccountClassificationPage {
  items: AccountingAccountClassification[]
  page: number
  size: number
  totalElements: number
  totalPages: number

  totalAccounts: number
  pendingAccounts: number
  classifiedAccounts: number
}

export interface DreClassification {
    id: string
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
}

export type ClassificationImportStatus =
    | "READY"
    | "ALREADY_CLASSIFIED"
    | "MAPPING_NOT_FOUND"
    | "CLASSIFICATION_NOT_FOUND"
    | "CONFLICT"

export interface ClassificationImportPreviewItem {
    accountId: string
    accountKey: string
    accountDescription: string
    status: ClassificationImportStatus

    currentClassificationId: string | null
    proposedClassificationId: string | null

    dreClassification: string | null
    dreOpenClassification: string | null
    ebitdaClassification: string | null
}

export interface ClassificationImportPreview {
    total: number
    ready: number
    alreadyClassified: number
    mappingNotFound: number
    classificationNotFound: number
    conflicts: number
    catalogAdditions: number

    items: ClassificationImportPreviewItem[]
}

export interface ClassificationImportApplyResponse {
    createdCatalogClassifications: number
    classifiedAccounts: number
    alreadyClassifiedAccounts: number
    mappingNotFoundAccounts: number
    conflicts: number
    unresolvedClassifications: number
}