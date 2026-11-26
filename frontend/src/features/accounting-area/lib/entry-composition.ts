/**
 * Composição de um lançamento (#475): as linhas do mesmo `sourceEntryKey` na
 * mesma fonte, na ordem da API. Nada é somado ou recalculado.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export interface EntryRef {
    accountingSourceId: string
    sourceEntryKey: string
}

export function entryCompositionLines<T extends EntryRef>(items: readonly T[], entry: EntryRef): T[] {
    return items.filter(
        (item) => item.accountingSourceId === entry.accountingSourceId && item.sourceEntryKey === entry.sourceEntryKey,
    )
}

/** Código cru da origem, complementado pela descrição técnica do catálogo quando houver: "FI — descrição". */
export function originLabel(code: string | null | undefined, catalog: Map<string, string> | undefined): string | null {
    if (code === null || code === undefined) {
        return null
    }

    const description = catalog?.get(code)

    return description ? `${code} — ${description}` : code
}
