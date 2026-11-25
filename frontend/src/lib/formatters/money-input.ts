/**
 * Converte um valor monetário digitado no padrão brasileiro ("1.500,00",
 * "R$ 12.000,5", "1500.75") na string decimal exata do contrato ("1500.00"),
 * sem passar por Number. Mesmas regras de parseOpeningBalance, sem sinal:
 * vírgula é a casa decimal; sem vírgula, pontos em grupos de três são milhar.
 * Mais de duas casas decimais é recusado (o backend recusa fração de centavo).
 */
export function parseMoneyInput(input: string): string | null {
    let text = input.trim().replace(/R\$\s?/, "").replace(/\s/g, "")

    if (text.includes(",")) {
        text = text.replace(/\./g, "").replace(",", ".")
    } else if (/^\d{1,3}(\.\d{3})+$/.test(text)) {
        text = text.replace(/\./g, "")
    }

    const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(text)

    if (!match) {
        return null
    }

    const [, integer, fraction] = match

    return fraction ? `${integer}.${fraction}` : integer
}
