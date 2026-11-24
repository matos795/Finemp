/**
 * Busca do menu (menuNorm/menuFiltrar do protótipo): sem acento e sem
 * diferenciar maiúsculas, casando aba, grupo ou subgrupo.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export function menuNorm(text: string) {
    return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
}

export function menuMatches(query: string, screenLabel: string, groupLabel: string, subgroupLabel: string) {
    const q = menuNorm(query).trim()

    return q.length === 0 || menuNorm(`${screenLabel} ${groupLabel} ${subgroupLabel}`).includes(q)
}
