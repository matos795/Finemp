import { useQuery } from "@tanstack/react-query"
import { getReadableAccountingSources } from "../api/accounting-data-api"
import { accountingV2Access } from "../lib/accounting-v2"
import { accountingQueryKey } from "../lib/query-keys"
import { useSessionUserId } from "./use-accounting-area-queries"

export interface AccountingSourceOption {
    id: string
    label: string
}

/**
 * Fontes contábeis da empresa visíveis para leitura.
 * A autoridade de acesso é sempre o backend; a UI só evita consulta quando
 * nenhuma permissão contábil de leitura faz sentido para a tela.
 */
export function useAccountingSources(companyId: string | null, permissions: readonly string[]) {
    const userId = useSessionUserId()
    const access = accountingV2Access(permissions)
    const canReadSources =
        access.coverage ||
        permissions.includes("DRE_VIEW")

    const sources = useQuery({
        queryKey: accountingQueryKey(userId, "readable-sources", companyId),
        queryFn: () => getReadableAccountingSources(companyId as string),
        enabled: companyId !== null && canReadSources,
        staleTime: 0,
        retry: false,
    })

    const options: AccountingSourceOption[] = (sources.data ?? []).map((source, index) => {
        const reference = source.externalReference?.trim()
        const suffix = reference ? ` · ${reference}` : ""

        return {
            id: source.id,
            label: `Fonte ${index + 1} · ${source.type}${suffix}`,
        }
    })

    return {
        options,
        loading: companyId !== null && canReadSources && sources.isLoading,
    }
}

/** Fonte efetiva: a escolhida, se ainda visível; senão a primeira. */
export function effectiveSource(options: AccountingSourceOption[], chosen: string | null) {
    return options.find((option) => option.id === chosen)?.id ?? options[0]?.id ?? null
}
