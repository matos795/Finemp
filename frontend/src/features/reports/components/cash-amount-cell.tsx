import {
    formatExactDecimalCell,
    isNegativeExactDecimal,
} from "../../../lib/formatters/decimal"

/** Célula numérica (.n) com o valor exato do backend; só formata. */
export function CashAmountCell({
    value,
    tone,
}: {
    value: string | undefined
    /** Cor fixa da coluna no protótipo (Recebido verde, Pago vermelho). */
    tone?: "pos" | "neg"
}) {
    if (value === undefined) {
        return <td className="n">—</td>
    }

    const className = [
        "n",
        tone ?? (isNegativeExactDecimal(value) ? "neg" : ""),
    ].filter(Boolean).join(" ")

    return <td className={className}>{formatExactDecimalCell(value)}</td>
}
