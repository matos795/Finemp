/**
 * Chaves de cache da Área da Contabilidade. Todas começam por
 * ["accounting-area", <id do usuário da sessão>]: trocar de usuário sem
 * recarregar a página nunca reaproveita a carteira, a elegibilidade ou os
 * dados de outro usuário, e o logout remove tudo pelo prefixo.
 *
 * Módulo sem dependências de runtime: é importado pelos testes node.
 */
export const ACCOUNTING_AREA_QUERY_ROOT = "accounting-area"

export function accountingQueryKey(userId: string, ...parts: unknown[]) {
    return [ACCOUNTING_AREA_QUERY_ROOT, userId, ...parts] as const
}
