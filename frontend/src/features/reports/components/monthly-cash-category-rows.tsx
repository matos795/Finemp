import {
    CATEGORY_PAGE_SIZE,
    useMonthlyCashCategoryTotals,
} from "../hooks/use-cash-totals"
import { formatExactDecimalCell } from "../../../lib/formatters/decimal"
import type {
    CashActivity,
    CashDirection,
    CashState,
    MonthlyCashCategoryTotal,
} from "../types/cash-totals.types"

export interface CategoryCellTarget {
    categoryId: string
    label: string
    monthIndex: number
}

interface MonthlyCashCategoryRowsProps {
    companyId: string | undefined
    year: number
    direction: CashDirection
    state: CashState
    activity: CashActivity
    monthIndexes: number[]
    /** Colunas extras (Total e Média) que seguem pendentes. */
    pendingColumns: number
    onOpenTitles: (target: CategoryCellTarget) => void
}

function categoryLabel(category: MonthlyCashCategoryTotal) {
    const name = category.categoryName ?? "Categoria sem nome"

    return category.categoryCode ? `${category.categoryCode} ${name}` : name
}

/**
 * Categorias de uma linha aberta do fluxo mensal. Cada célula é o subtotal
 * do servidor para o mês; categorias sem título no mês aparecem como "–".
 * O subtotal por grupo não vem da API e não é somado aqui (PA4).
 */
export function MonthlyCashCategoryRows({
    companyId,
    year,
    direction,
    state,
    activity,
    monthIndexes,
    pendingColumns,
    onOpenTitles,
}: MonthlyCashCategoryRowsProps) {
    const months = useMonthlyCashCategoryTotals(
        companyId,
        year,
        direction,
        state,
        activity,
        true,
    )

    const columns = monthIndexes.length + 1 + pendingColumns

    if (months.some((month) => month.isError)) {
        return (
            <tr className="sub">
                <td
                    colSpan={columns}
                    className="neg"
                >
                    Não foi possível carregar as categorias desta linha.
                </td>
            </tr>
        )
    }

    if (months.some((month) => month.isPending)) {
        return (
            <tr className="sub">
                <td colSpan={columns}>Carregando categorias…</td>
            </tr>
        )
    }

    const categories = new Map<string, MonthlyCashCategoryTotal>()
    const cells = new Map<string, MonthlyCashCategoryTotal>()

    months.forEach((month, index) => {
        month.data?.items.forEach((item) => {
            if (!categories.has(item.categoryId)) {
                categories.set(item.categoryId, item)
            }

            cells.set(`${item.categoryId}|${index}`, item)
        })
    })

    const ordered = [...categories.values()].sort((left, right) =>
        categoryLabel(left).localeCompare(categoryLabel(right), "pt", { numeric: true }),
    )

    const truncated = months.some(
        (month) => (month.data?.totalPages ?? 0) > 1,
    )

    if (ordered.length === 0) {
        return (
            <tr className="sub">
                <td colSpan={columns}>Nenhuma categoria com valores no ano.</td>
            </tr>
        )
    }

    return (
        <>
            {ordered.map((category) => {
                const label = categoryLabel(category)

                return (
                    <tr
                        key={category.categoryId}
                        className="sub"
                    >
                        <td
                            style={{ paddingLeft: 52 }}
                            title={category.groupName ?? undefined}
                        >
                            {label}
                        </td>

                        {monthIndexes.map((index) => {
                            const cell = cells.get(`${category.categoryId}|${index}`)

                            return (
                                <td
                                    key={index}
                                    className="n"
                                >
                                    {cell ? (
                                        <a
                                            href="#"
                                            style={{ color: "inherit", textDecoration: "none" }}
                                            title={`${cell.titleCount} título(s) – clique para ver`}
                                            onClick={(event) => {
                                                event.preventDefault()
                                                onOpenTitles({
                                                    categoryId: category.categoryId,
                                                    label,
                                                    monthIndex: index,
                                                })
                                            }}
                                        >
                                            {formatExactDecimalCell(cell.amount)}
                                        </a>
                                    ) : "–"}
                                </td>
                            )
                        })}

                        {Array.from({ length: pendingColumns }, (_, index) => (
                            <td
                                key={index}
                                className={index === 1 ? "n med" : "n"}
                            />
                        ))}
                    </tr>
                )
            })}

            {truncated ? (
                <tr className="sub">
                    <td
                        colSpan={columns}
                        className="mut"
                        style={{ fontSize: 12 }}
                    >
                        Algum mês tem mais de {CATEGORY_PAGE_SIZE} categorias; só as primeiras aparecem.
                    </td>
                </tr>
            ) : null}
        </>
    )
}
