import { formatExactDecimalCell } from "../../../lib/formatters/decimal"
import { useOpenTitlesOfPartner } from "../hooks/use-open-titles"
import type {
    OpenTitlePartnerTotal,
    OpenTitlesFilter,
} from "../types/aging.types"
import { OpenTitleRow } from "./open-title-row"

interface OpenTitlePartnerGroupProps {
    companyId: string | undefined
    filter: OpenTitlesFilter
    group: OpenTitlePartnerTotal
    isIncome: boolean
}

function loadedTitles(
    titles: ReturnType<typeof useOpenTitlesOfPartner>,
) {
    const seen = new Set<string>()

    return (titles.data?.pages ?? [])
        .flatMap((page) => page.items)
        .filter((title) => {
            if (seen.has(title.id)) {
                return false
            }

            seen.add(title.id)
            return true
        })
}

/**
 * Grupo por cliente/fornecedor do relatório. O backend calcula subtotal,
 * ordenação e paginação; a UI apenas carrega páginas adicionais.
 */
export function OpenTitlePartnerGroup({
    companyId,
    filter,
    group,
    isIncome,
}: OpenTitlePartnerGroupProps) {
    const titles =
        useOpenTitlesOfPartner(companyId, filter, group.partnerId)

    const name = group.partnerName ?? ""
    const items = loadedTitles(titles)

    return (
        <>
            {titles.isPending ? (
                <tr>
                    <td colSpan={9} className="mut">
                        Carregando títulos de {name || "Sem cliente/fornecedor"}…
                    </td>
                </tr>
            ) : titles.isError && !titles.data ? (
                <tr>
                    <td colSpan={9} className="neg">
                        Não foi possível carregar os títulos de {name || "Sem cliente/fornecedor"}.
                    </td>
                </tr>
            ) : (
                items.map((title) => (
                    <OpenTitleRow
                        key={title.id}
                        title={title}
                        partnerName={name}
                        isIncome={isIncome}
                    />
                ))
            )}

            {titles.hasNextPage && (
                <tr>
                    <td colSpan={9}>
                        <button
                            type="button"
                            className="btn sec sm"
                            disabled={titles.isFetchingNextPage}
                            onClick={() => void titles.fetchNextPage()}
                        >
                            {titles.isFetchingNextPage
                                ? "Carregando títulos…"
                                : "Carregar mais títulos"}
                        </button>
                    </td>
                </tr>
            )}

            {titles.isFetchNextPageError && (
                <tr>
                    <td colSpan={9} className="neg">
                        Não foi possível carregar mais títulos. Tente novamente.
                    </td>
                </tr>
            )}

            <tr className="tot">
                <td colSpan={7}>
                    Subtotal {name || "Sem cliente/fornecedor"}
                </td>
                <td className="n">
                    {formatExactDecimalCell(group.nominalAmount)}
                </td>
                <td />
            </tr>
        </>
    )
}
