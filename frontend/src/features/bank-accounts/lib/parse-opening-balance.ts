export type ParsedOpeningBalance =
    | { ok: true, value: string }
    | { ok: false, message: string }

/**
 * Converte o texto digitado (padrão brasileiro, como o parseValor do
 * protótipo: "1.234,56", "-10", "R$ 5,5") na string decimal exata do
 * contrato ("1234.56"), sem converter para Number.
 *
 * O protótipo arredondava para centavos; aqui valores com mais de duas
 * casas são recusados (o backend também recusa frações de centavo). PA10.
 */
export function parseOpeningBalance(
    input: string,
): ParsedOpeningBalance {
    let text = input
        .trim()
        .replace(/R\$\s?/, "")
        .replace(/\s/g, "")

    if (!text) {
        return { ok: false, message: "Valor inválido." }
    }

    if (text.includes(",")) {
        text = text.replace(/\./g, "").replace(",", ".")
    } else if (/^-?\d{1,3}(\.\d{3})+$/.test(text)) {
        text = text.replace(/\./g, "")
    }

    const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(text)

    if (!match) {
        return { ok: false, message: "Valor inválido." }
    }

    const [, sign, integer, fraction = ""] = match

    if (fraction.length > 2) {
        return {
            ok: false,
            message: "Informe o saldo inicial com no máximo duas casas decimais.",
        }
    }

    return {
        ok: true,
        value: `${sign}${integer}${fraction ? `.${fraction}` : ""}`,
    }
}
