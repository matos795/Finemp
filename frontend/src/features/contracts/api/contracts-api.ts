import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    Contract,
    ContractFilter,
    ContractPage,
    ContractPartnerOptionPage,
    ContractValues,
} from "../types/contract.types.ts"

export const CONTRACTS_PAGE_SIZE = 50

function contractsPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/contracts`
}

export function listContracts(
    companyId: string,
    filter: ContractFilter,
    page: number,
    signal?: AbortSignal,
): Promise<ContractPage> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(CONTRACTS_PAGE_SIZE),
    })

    const search = filter.search.trim()
    if (search) params.set("search", search)
    if (filter.active) params.set("active", filter.active)

    return apiRequest<ContractPage>(
        `${contractsPath(companyId)}?${params}`,
        { signal },
    )
}

export function listContractPartnerOptions(
    companyId: string,
    search: string,
    signal?: AbortSignal,
): Promise<ContractPartnerOptionPage> {
    const params = new URLSearchParams({
        page: "0",
        size: String(CONTRACTS_PAGE_SIZE),
    })

    const text = search.trim()
    if (text) params.set("search", text)

    return apiRequest<ContractPartnerOptionPage>(
        `${contractsPath(companyId)}/partners?${params}`,
        { signal },
    )
}

export function createContract(
    companyId: string,
    values: ContractValues,
): Promise<Contract> {
    return apiRequest<Contract>(contractsPath(companyId), {
        method: "POST",
        body: JSON.stringify({
            ...values,
            expectedVersion: null,
        }),
    })
}

export function updateContract(
    companyId: string,
    contract: Contract,
    values: ContractValues,
): Promise<Contract> {
    return apiRequest<Contract>(
        `${contractsPath(companyId)}/${encodeURIComponent(contract.id)}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion: contract.version,
            }),
        },
    )
}
