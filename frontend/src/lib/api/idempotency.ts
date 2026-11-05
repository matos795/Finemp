/** Idempotency-Key (UUID) exigida pelo OpenAPI nos comandos financeiros. */
export function newIdempotencyKey() {
    return crypto.randomUUID()
}
