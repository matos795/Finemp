import { apiRequest } from "../../../lib/api/api-client.ts"
import type {
    Asset,
    AssetFilter,
    AssetPage,
    AssetSummary,
    AssetValues,
} from "../types/asset.types.ts"

export const ASSETS_PAGE_SIZE = 50

function assetsPath(companyId: string) {
    return `/api/companies/${encodeURIComponent(companyId)}/assets`
}

export function listAssets(
    companyId: string,
    filter: AssetFilter,
    page: number,
    signal?: AbortSignal,
): Promise<AssetPage> {
    const params = new URLSearchParams({
        page: String(page),
        size: String(ASSETS_PAGE_SIZE),
    })

    const search = filter.search.trim()
    if (search) params.set("search", search)
    if (filter.status) params.set("status", filter.status)
    if (filter.category) params.set("category", filter.category)

    return apiRequest<AssetPage>(
        `${assetsPath(companyId)}?${params}`,
        { signal, cache: "no-store" },
    )
}

export function getAssetSummary(
    companyId: string,
    signal?: AbortSignal,
): Promise<AssetSummary> {
    return apiRequest<AssetSummary>(
        `${assetsPath(companyId)}/summary`,
        { signal, cache: "no-store" },
    )
}

export function createAsset(
    companyId: string,
    values: AssetValues,
): Promise<Asset> {
    return apiRequest<Asset>(assetsPath(companyId), {
        method: "POST",
        body: JSON.stringify({
            ...values,
            expectedVersion: null,
        }),
    })
}

export function updateAsset(
    companyId: string,
    asset: Asset,
    values: AssetValues,
): Promise<Asset> {
    return apiRequest<Asset>(
        `${assetsPath(companyId)}/${encodeURIComponent(asset.id)}`,
        {
            method: "PUT",
            body: JSON.stringify({
                ...values,
                expectedVersion: asset.version,
            }),
        },
    )
}
