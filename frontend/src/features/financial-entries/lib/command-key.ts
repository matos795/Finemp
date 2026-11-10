import { newIdempotencyKey } from "../../../lib/api/idempotency.ts"

/** Chave usada no último envio e o corpo que ela identifica. */
export interface CommandAttempt {
    body: string
    key: string
}

/**
 * Idempotency-Key do próximo envio. Repetir exatamente o mesmo corpo
 * (ex.: depois de uma resposta perdida) reaproveita a chave, e o backend
 * devolve o mesmo recibo. Qualquer mudança no corpo gera chave nova.
 */
export function nextCommandAttempt(
    previous: CommandAttempt | null,
    body: string,
    newKey: () => string = newIdempotencyKey,
): CommandAttempt {
    if (previous && previous.body === body) {
        return previous
    }

    return { body, key: newKey() }
}
