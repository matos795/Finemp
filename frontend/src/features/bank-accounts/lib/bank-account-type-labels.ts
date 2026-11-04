import type { BankAccountType } from "../types/bank-account.types"

/**
 * Rótulos do protótipo homologado (Cadastros › Contas bancárias),
 * na mesma ordem do OpenAPI.
 */
const bankAccountTypeLabels: Record<BankAccountType, string> = {
    CURRENT: "Conta corrente",
    SAVINGS: "Poupança",
    CASH: "Caixa",
    INVESTMENT: "Aplicação",
    DIGITAL: "Conta digital",
    OTHER: "Outra",
}

/** Tipos do contrato, na ordem das opções do protótipo. */
export const bankAccountTypes = Object.keys(
    bankAccountTypeLabels,
) as BankAccountType[]

/** Valor desconhecido é preservado e exibido como veio da API. */
export function getBankAccountTypeLabel(
    type: string,
) {
    return (
        bankAccountTypeLabels[type as BankAccountType] ?? type
    )
}
