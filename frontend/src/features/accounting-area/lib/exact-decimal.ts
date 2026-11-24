/**
 * Formatação pt-BR de valores que a API devolve como string decimal exata
 * (ex.: "1250.5000"). Não converte para number: nada é arredondado. Mostra
 * pelo menos 2 casas e mantém as demais quando não são zero ("100,0001").
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export function formatExactDecimal(value: string): string {
    const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value.trim())

    if (!match) {
        return value
    }

    const [, sign, integer, fraction = ""] = match
    const grouped = integer.replace(/^0+(?=\d)/, "").replace(/\B(?=(\d{3})+(?!\d))/g, ".")
    const trimmed = fraction.replace(/0+$/, "")
    const decimals = trimmed.length < 2 ? trimmed.padEnd(2, "0") : trimmed
    const isZero = /^0+$/.test(integer) && /^0*$/.test(fraction)

    return `${sign && !isZero ? "-" : ""}${grouped},${decimals}`
}

export function isNegativeDecimal(value: string): boolean {
    return /^-/.test(value.trim()) && !/^-0+(\.0+)?$/.test(value.trim())
}
