import { apiRequest } from "../../../lib/api/api-client"
import type { DrePeriod, DreResult } from "../types/dre.types"

export async function getDre(
    companyId: string,
    period: DrePeriod,
): Promise<DreResult> {
    const params = new URLSearchParams({
        startDate: period.startDate,
        endDate: period.endDate,
    })

    return apiRequest<DreResult>(
        `/api/companies/${companyId}/dre?${params}`,
    )
}