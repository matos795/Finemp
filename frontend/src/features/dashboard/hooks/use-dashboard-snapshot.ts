import { useQuery } from "@tanstack/react-query"
import {
    getPresentationSnapshot,
    type SnapshotQuery,
} from "../../../lib/api/presentation-client"

/**
 * Snapshot financeiro do Painel (presentation-snapshot), pelo client
 * oficial. A chave fica sob "financial-reports" da Company: baixas e
 * estornos já a invalidam. Trocar Company ou mês cancela a requisição
 * anterior (signal) e nunca reaproveita dados de outra Company.
 */
export function useDashboardSnapshot(
    companyId: string | undefined,
    query: SnapshotQuery,
    enabled: boolean,
) {
    return useQuery({
        queryKey: ["companies", companyId, "financial-reports", "presentation-snapshot", query],
        queryFn: ({ signal }) => getPresentationSnapshot(companyId!, query, signal),
        enabled: enabled && Boolean(companyId),
        retry: false,
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId ? previousData : undefined,
    })
}
