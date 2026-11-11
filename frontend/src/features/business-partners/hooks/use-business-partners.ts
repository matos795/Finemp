import {
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query"
import {
    createBusinessPartner,
    deleteBusinessPartner,
    listBusinessPartnersPage,
    updateBusinessPartner,
} from "../api/business-partners-api"
import type {
    BusinessPartner,
    BusinessPartnerFilter,
    BusinessPartnerProfile,
} from "../types/business-partner.types"

/** Página de cadastros da Company ativa; nunca reaproveita outra Company. */
export function useBusinessPartners(
    companyId: string | undefined,
    filter: BusinessPartnerFilter,
    page: number,
) {
    return useQuery({
        queryKey: ["companies", companyId, "business-partners", "page", { filter, page }],
        queryFn: ({ signal }) => listBusinessPartnersPage(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previousData, previousQuery) =>
            previousQuery?.queryKey[1] === companyId ? previousData : undefined,
    })
}

/**
 * Incluir, editar e excluir/inativar. Toda escrita recarrega os cadastros
 * da Company (inclusive os seletores do lançamento): o servidor é a fonte
 * da verdade.
 */
export function useBusinessPartnerMutations(companyId: string) {
    const queryClient = useQueryClient()

    const refresh = () =>
        queryClient.invalidateQueries({ queryKey: ["companies", companyId, "business-partners"] })

    const save = useMutation({
        mutationFn: ({ profile, original }: { profile: BusinessPartnerProfile, original?: BusinessPartner }) =>
            original
                ? updateBusinessPartner(companyId, original.id, profile, original.version)
                : createBusinessPartner(companyId, profile),
        onSettled: refresh,
    })

    const remove = useMutation({
        mutationFn: (partner: BusinessPartner) => deleteBusinessPartner(companyId, partner.id, partner.version),
        onSettled: refresh,
    })

    return { save, remove }
}
