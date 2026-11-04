const exactDecimalPattern =
    /^(-?)(\d+)(?:\.(\d{1,2}))?$/

/**
 * Formata uma string decimal exata da API (ex.: "-1234.50")
 * no padrão pt-BR com duas casas ("-1.234,50"), sem converter
 * para Number: nenhuma conta é feita e não há perda de precisão.
 *
 * Valores fora do formato esperado são exibidos como vieram.
 */
export function formatExactDecimal(
    value: string,
) {
    const match =
        exactDecimalPattern.exec(value.trim())

    if (!match) {
        return value
    }

    const [, sign, integerPart, fractionPart = ""] = match

    const integer =
        integerPart.replace(/^0+(?=\d)/, "")

    const grouped =
        integer.replace(/\B(?=(\d{3})+(?!\d))/g, ".")

    const fraction =
        fractionPart.padEnd(2, "0")

    const isZero =
        /^0+$/.test(integer + fraction)

    return `${isZero ? "" : sign}${grouped},${fraction}`
}

const isoDatePattern =
    /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * Exibe uma data ISO (YYYY-MM-DD) como dd/mm/aaaa sem usar Date,
 * evitando deslocamento de fuso horário.
 */
export function formatIsoDate(
    value: string,
) {
    const match = isoDatePattern.exec(value)

    if (!match) {
        return value
    }

    const [, year, month, day] = match

    return `${day}/${month}/${year}`
}

/**
 * Célula de tabela no padrão do protótipo (fmtN): zero aparece como "–".
 * Recebe a string decimal exata da API; só formata.
 */
export function formatExactDecimalCell(
    value: string,
) {
    return /^-?(?:0+(?:\.0*)?|\.0+)$/.test(value.trim())
        ? "–"
        : formatExactDecimal(value)
}

const amountFormatter =
    new Intl.NumberFormat("pt-BR", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    })

/**
 * Célula para valores que a API ainda entrega como número JSON
 * (ex.: DRE contábil). Só formata; zero aparece como "–".
 */
export function formatAmountCell(
    value: number,
) {
    if (!Number.isFinite(value)) {
        return "—"
    }

    if (Math.abs(value) < 0.005) {
        return "–"
    }

    return amountFormatter.format(value)
}

/** Moeda no padrão do protótipo (fmtBRL): "-R$ 1.234,56". Só formata. */
export function formatExactCurrency(
    value: string,
) {
    if (!exactDecimalPattern.test(value.trim())) {
        return value
    }

    const formatted = formatExactDecimal(value)

    return formatted.startsWith("-")
        ? `-R$ ${formatted.slice(1)}`
        : `R$ ${formatted}`
}
