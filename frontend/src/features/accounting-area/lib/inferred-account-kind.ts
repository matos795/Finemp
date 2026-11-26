/**
 * Interpretação INFERIDA de `accountType` (TIPOCONTA do Questor), conforme
 * docs/architecture/canonical-accounting-contract-v2.md: 1 = agrupadora/sintética,
 * 2 = lançável/analítica, observado na base (C1b) e não documentado pelo
 * fornecedor. Nunca é exibida como campo oficial da origem: sempre com o selo
 * "Inferido". Qualquer outro código fica sem interpretação.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export function inferredAccountKind(accountType: string | null | undefined) {
    const code = accountType?.trim()

    if (code === "1") return { label: "Sintética", tag: "Inferido" } as const
    if (code === "2") return { label: "Analítica", tag: "Inferido" } as const

    return null
}

export const INFERRED_ACCOUNT_KIND_NOTE =
    "Sintética/analítica é uma interpretação inferida do código accountType (1 = agrupadora, 2 = lançável), " +
    "observada na base e ainda não documentada pela origem."
