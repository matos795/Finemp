import { useMonthlyCashCategoryTotals } from "../hooks/use-cash-totals"
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
    if (!category.categoryId) {
        return "Sem categoria"
    }

    const name = category.categoryName ?? "Categoria sem nome"

    return category.categoryCode ? `${category.categoryCode} ${name}` : name
}

function categoryKey(categoryId: string | null) {
    return categoryId ?? "__unassigned__"
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
        month.data?.forEach((item) => {
            const key = categoryKey(item.categoryId)

            if (!categories.has(key)) {
                categories.set(key, item)
            }

            cells.set(`${key}|${index}`, item)
        })
    })

    const ordered = [...categories.values()].sort((left, right) =>
        categoryLabel(left).localeCompare(categoryLabel(right), "pt", { numeric: true }),
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
                        key={categoryKey(category.categoryId)}
                        className="sub"
                    >
                        <td
                            style={{ paddingLeft: 52 }}
                            title={category.groupName ?? undefined}
                        >
                            {label}
                        </td>

                        {monthIndexes.map((index) => {
                            const cell = cells.get(`${categoryKey(category.categoryId)}|${index}`)

                            return (
                                <td
                                    key={index}
                                    className="n"
                                >
                                    {!cell ? "–" : category.categoryId ? (
                                        <a
                                            href="#"
                                            style={{ color: "inherit", textDecoration: "none" }}
                                            title={`${cell.titleCount} título(s) – clique para ver`}
                                            onClick={(event) => {
                                                event.preventDefault()
                                                onOpenTitles({
                                                    categoryId: category.categoryId!,
                                                    label,
                                                    monthIndex: index,
                                                })
                                            }}
                                        >
                                            {formatExactDecimalCell(cell.amount)}
                                        </a>
                                    ) : (
                                        <span title="Detalhamento de títulos sem categoria ainda não existe na API">
                                            {formatExactDecimalCell(cell.amount)}
                                        </span>
                                    )}
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

        </>
    )
}
