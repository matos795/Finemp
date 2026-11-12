import {
    apiErrorCode,
    apiErrorStatus,
} from "../../../lib/api/api-errors"

export function describeStatementResolutionError(error: unknown) {
    const code = apiErrorCode(error)

    if (code === "FINANCIAL_PERIOD_CLOSED") {
        return "O período financeiro deste movimento está fechado. Nada foi alterado."
    }

    if (code === "NOT_A_CANDIDATE") {
        return "Este lançamento não corresponde mais ao movimento. As sugestões foram atualizadas."
    }

    if (code === "STALE_VERSION" || code === "INVALID_STATE") {
        return "O movimento ou lançamento foi alterado por outra pessoa. A lista foi atualizada."
    }

    switch (apiErrorStatus(error)) {
        case 400:
            return "Confira os dados do lançamento."
        case 403:
            return "Você não tem permissão para conciliar extratos nesta empresa."
        case 404:
            return "O movimento ou lançamento não existe mais, ou a conciliação não está disponível neste ambiente."
        case 409:
            return "Não foi possível concluir por conflito de estado. A lista foi atualizada."
        case 429:
            return "Muitas operações ao mesmo tempo. Tente novamente em instantes."
        default:
            return "Não foi possível resolver este movimento do extrato."
    }
}
