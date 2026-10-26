import { apiRequest } from "../../../lib/api/api-client"

import type {
    AccountingAccountClassificationPage,
    ClassificationImportApplyResponse,
    ClassificationImportPreview,
    ClassificationStatus,
    DreClassification,
} from "../types/classification.types"

export function getAccountClassifications(
  companyId: string,
  page: number,
  size: number,
  status?: ClassificationStatus,
  search?: string,
) {
  const params =
    new URLSearchParams({
      page: String(page),
      size: String(size),
    })

  if (status) {
    params.set("status", status)
  }

  if (search) {
    params.set("search", search)
  }

  return apiRequest<AccountingAccountClassificationPage>(
    `/api/companies/${companyId}/accounting-accounts/classifications?${params}`,
  )
}

export function getDreClassifications(
    companyId: string,
) {
    return apiRequest<DreClassification[]>(
        `/api/companies/${companyId}/dre-classifications`,
    )
}

export function classifyAccount(
    companyId: string,
    accountId: string,
    classificationId: string,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
            method: "PUT",
            body: JSON.stringify({
                classificationId,
            }),
        },
    )
}

export function unclassifyAccount(
    companyId: string,
    accountId: string,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
            method: "DELETE",
        },
    )
}

export function previewClassificationImport(
    companyId: string,
    file: File,
) {
    const formData =
        new FormData()

    formData.append(
        "file",
        file,
    )

    return apiRequest<ClassificationImportPreview>(
        `/api/companies/${companyId}/accounting-accounts/classification-import/preview`,
        {
            method: "POST",
            body: formData,
        },
    )
}

export function applyClassificationImport(
    companyId: string,
    file: File,
) {
    const formData =
        new FormData()

    formData.append(
        "file",
        file,
    )

    return apiRequest<ClassificationImportApplyResponse>(
        `/api/companies/${companyId}/accounting-accounts/classification-import/apply`,
        {
            method: "POST",
            body: formData,
        },
    )
}