import { ApiError } from "../../../lib/api/api-client.ts"

export type FinancialEntryOperation = "list" | "create" | "update" | "settle" | "reverse"

/**
 * Mensagem para o usuário a partir do status e do `code` do ProblemDetail.
 * Os códigos internos não aparecem na tela.
 */
export function describeFinancialEntryError(
    error: unknown,
    operation: FinancialEntryOperation,
): string {
    if (!(error instanceof ApiError)) {
        return operation === "list"
            ? "Não foi possível carregar os lançamentos."
            : "Não foi possível salvar. Verifique a conexão e tente de novo."
    }

    if (operation === "reverse") {
        // Sem janela aberta: a orientação é recarregar a lista e repetir.
        if (error.code === "STALE_VERSION" || error.code === "IDEMPOTENCY_MISMATCH") {
            return "Este lançamento foi alterado por outra operação. Recarregue a lista e tente de novo."
        }

        if (error.status === 400) {
            return "O sistema recusou o pedido. Recarregue a lista e tente de novo."
        }

        if (error.status === 403) {
            return "Você não tem permissão para desfazer baixas nesta empresa."
        }
    }

    switch (error.code) {
        case "STALE_VERSION":
            return "Este lançamento foi alterado depois que você o abriu. Feche a janela, recarregue a lista e abra o lançamento de novo antes de salvar."
        case "FINANCIAL_PERIOD_CLOSED":
            return "O período financeiro deste lançamento está fechado. A operação não pode ser realizada."
        case "FINANCIAL_ENTRY_CONFLICT":
            return "Dados ligados a este lançamento mudaram. Recarregue a lista e tente de novo."
        case "IDEMPOTENCY_MISMATCH":
            return "Esta operação já foi enviada com outros dados. Feche a janela, recarregue a lista e tente de novo."
    }

    switch (error.status) {
        case 400:
            if (operation === "settle") {
                return "Confira a data, o valor e a conta: o sistema recusou algum deles."
            }

            return operation === "list"
                ? "Confira os filtros (a data final não pode ser anterior à inicial)."
                : "Confira os campos: o sistema recusou algum valor (categoria, conta, cliente/fornecedor ou centro de resultado)."
        case 401:
            return "Sua sessão terminou. Entre de novo."
        case 403:
            return operation === "list"
                ? "Você não tem acesso aos lançamentos desta empresa."
                : operation === "settle"
                    ? "Você não tem permissão para registrar pagamentos e recebimentos desta empresa."
                    : "Você não tem permissão para alterar lançamentos desta empresa."
        case 404:
            return operation === "update" || operation === "settle" || operation === "reverse"
                ? "Lançamento não encontrado. Ele pode ter sido excluído; recarregue a lista."
                : "Lançamentos não estão disponíveis neste ambiente."
        case 409:
            return "O lançamento mudou ou está em uso. Recarregue a lista e tente de novo."
        case 413:
            return "Os dados enviados são grandes demais. Encurte a observação."
        case 429:
            return "Há outras operações em andamento. Aguarde um instante e tente de novo."
        case 503:
            return "O serviço de lançamentos está indisponível no momento. Tente de novo em instantes."
    }

    if (operation === "settle") {
        return "Não foi possível registrar a baixa."
    }

    if (operation === "reverse") {
        return "Não foi possível desfazer a baixa."
    }

    return operation === "list"
        ? "Não foi possível carregar os lançamentos."
        : "Não foi possível salvar o lançamento."
}

/** 401 encerra a sessão; o formulário não deve só mostrar a mensagem. */
export function isSessionError(error: unknown) {
    return error instanceof ApiError && error.status === 401
}
