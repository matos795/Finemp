import type {
    DreLine,
    OperationalDre,
} from "../../../lib/api/presentation-client"

export type OperationalDreLine = Exclude<DreLine, "NAO">

export type OperationalDreSubtotal = keyof OperationalDre["subtotals"]

/** Linhas do protótipo (DRE_LINHAS), só rótulos. */
export const dreLineLabels: Record<OperationalDreLine, string> = {
    REC: "Receita bruta",
    DED: "(−) Deduções e impostos sobre vendas",
    CUS: "(−) Custos das mercadorias, produtos e serviços",
    DCOM: "Despesas comerciais",
    DADM: "Despesas administrativas",
    DPES: "Despesas com pessoal",
    DTRI: "Impostos e taxas",
    OUT: "Outras receitas e despesas operacionais",
    DEP: "(−) Depreciação e amortização",
    RFIN: "Receitas financeiras",
    DFIN: "Despesas financeiras",
    IR: "(−) IR e CSLL",
}

/**
 * Estrutura do protótipo (DRE_EST). Linhas e subtotais vêm prontos da
 * API (fórmulas F07 no backend). Cabeçalhos com soma e margens ainda não
 * vêm da API e ficam pendentes, sem cálculo no frontend.
 */
export type DreStructureItem =
    | { type: "line"; line: OperationalDreLine; sub?: boolean }
    | { type: "subtotal"; label: string; subtotal: OperationalDreSubtotal }
    | { type: "pending"; label: string; kind: "header" | "margin" }

export const dreStructure: DreStructureItem[] = [
    { type: "line", line: "REC" },
    { type: "line", line: "DED" },
    { type: "subtotal", label: "= Receita líquida", subtotal: "netRevenue" },
    { type: "line", line: "CUS" },
    { type: "subtotal", label: "= Lucro bruto", subtotal: "grossProfit" },
    { type: "pending", label: "Margem bruta", kind: "margin" },
    { type: "pending", label: "(−) Despesas operacionais", kind: "header" },
    { type: "line", line: "DCOM", sub: true },
    { type: "line", line: "DADM", sub: true },
    { type: "line", line: "DPES", sub: true },
    { type: "line", line: "DTRI", sub: true },
    { type: "line", line: "OUT", sub: true },
    { type: "subtotal", label: "= EBITDA", subtotal: "ebitda" },
    { type: "pending", label: "Margem EBITDA", kind: "margin" },
    { type: "line", line: "DEP" },
    { type: "subtotal", label: "= Resultado operacional (EBIT)", subtotal: "ebit" },
    { type: "pending", label: "Resultado financeiro", kind: "header" },
    { type: "line", line: "RFIN", sub: true },
    { type: "line", line: "DFIN", sub: true },
    { type: "subtotal", label: "= Resultado antes do IR e CSLL", subtotal: "profitBeforeTax" },
    { type: "line", line: "IR" },
    { type: "subtotal", label: "= Resultado líquido", subtotal: "netResult" },
    { type: "pending", label: "Margem líquida", kind: "margin" },
]
