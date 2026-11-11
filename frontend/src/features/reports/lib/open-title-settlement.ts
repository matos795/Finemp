import type { CompanyPermission } from "../../auth/types/auth.types.ts"
import type { SettlementTarget } from "../../financial-entries/lib/financial-entry-settlement.ts"
import type { OpenTitle } from "../types/aging.types.ts"

/**
 * "Receber"/"Pagar" no relatório de títulos em aberto: só com
 * FINANCIAL_ENTRY_MANAGE e para título ainda sem baixa. Quem só vê o
 * relatório continua em leitura. O papel do usuário não entra na decisão.
 */
export function canSettleOpenTitle(
    title: Pick<OpenTitle, "paymentDate">,
    hasPermission: (permission: CompanyPermission) => boolean,
) {
    return title.paymentDate === null && hasPermission("FINANCIAL_ENTRY_MANAGE")
}

/**
 * Título do relatório → alvo da mesma janela de baixa de Lançamentos.
 * Usa só o que a lista já traz (id, versão, valor nominal, conta); não
 * busca o lançamento de novo nem inventa campos. O nome da conta não vem
 * na lista: sem catálogo de contas, a janela mostra "Conta atual".
 */
export function settlementTargetFromOpenTitle(
    title: Pick<
        OpenTitle,
        "id" | "version" | "direction" | "description" | "nominalAmount" | "dueDate" | "bankAccountId"
    >,
): SettlementTarget {
    return {
        id: title.id,
        version: title.version,
        direction: title.direction,
        description: title.description,
        nominalAmount: title.nominalAmount,
        dueDate: title.dueDate,
        bankAccountId: title.bankAccountId,
    }
}
