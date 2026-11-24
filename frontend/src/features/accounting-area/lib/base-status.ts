import { monthLabel, syncedUntil, type CompanySyncStatus } from "./accounting-period.ts"

/**
 * Texto de "Base contábil até" da barra de competência, sempre a partir de
 * fatos de sincronização (#440). Sem base ou sem acesso, diz isso.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export function baseStatusText(input: {
    hasCompany: boolean
    loading: boolean
    errorStatus: number | null
    status: CompanySyncStatus | null | undefined
}): string {
    if (!input.hasCompany) {
        return "Base contábil: escolha uma empresa"
    }

    if (input.loading) {
        return "Base contábil até …"
    }

    if (input.errorStatus === 403) {
        return "Base contábil: sem acesso à sincronização desta empresa"
    }

    if (input.errorStatus !== null) {
        return "Base contábil: não foi possível consultar a sincronização"
    }

    const until = syncedUntil(input.status)

    return until ? `Base contábil até ${monthLabel(until)}` : "Base contábil: nenhuma sincronização concluída"
}
