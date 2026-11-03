import type { AgingBandCode } from "../types/aging.types"

/** Faixas na ordem e com os nomes do protótipo (FAIXAS). */
export const agingBands: {
    code: AgingBandCode
    label: string
    overdue: boolean
}[] = [
    { code: "OVERDUE_OVER_90", label: "Vencido acima de 90 dias", overdue: true },
    { code: "OVERDUE_61_90", label: "Vencido 61 a 90 dias", overdue: true },
    { code: "OVERDUE_31_60", label: "Vencido 31 a 60 dias", overdue: true },
    { code: "OVERDUE_1_30", label: "Vencido 1 a 30 dias", overdue: true },
    { code: "DUE_0_30", label: "A vencer até 30 dias", overdue: false },
    { code: "DUE_31_60", label: "A vencer 31 a 60 dias", overdue: false },
    { code: "DUE_61_90", label: "A vencer 61 a 90 dias", overdue: false },
    { code: "DUE_OVER_90", label: "A vencer acima de 90 dias", overdue: false },
]

/** Faixa de vencidos pelo código do contrato (sem cálculo de datas). */
export function isOverdueBand(code: AgingBandCode) {
    return code.startsWith("OVERDUE_")
}
