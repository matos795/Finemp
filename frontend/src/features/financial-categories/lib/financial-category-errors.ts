import { ApiError } from "../../../lib/api/api-client.ts"
import { currentUserQueryKey } from "../../auth/hooks/use-current-user.ts"

export interface FinancialCategoryFailure {
    message: string
    /** Reler o catálogo da Company (o estado mudou no servidor). */
    refetch: boolean
    /** Fechar a janela: os dados dela ficaram desatualizados. */
    closeDialog: boolean
    sessionExpired: boolean
}

const GENERIC = "Não foi possível concluir a operação. Tente novamente em instantes."

/** Mensagens das ações do plano de contas; nunca expõem código ou corpo da API. */
export function describeFinancialCategoryError(error: unknown): FinancialCategoryFailure {
    const failure = (message: string, extra: Partial<FinancialCategoryFailure> = {}): FinancialCategoryFailure =>
        ({ message, refetch: false, closeDialog: false, sessionExpired: false, ...extra })

    if (!(error instanceof ApiError)) {
        return failure(GENERIC)
    }

    switch (error.code) {
        case "STALE_VERSION":
            return failure("Esta categoria foi alterada em outra sessão. A lista foi atualizada; abra de novo para revisar antes de salvar.",
                { refetch: true, closeDialog: true })
        case "CATEGORY_IN_USE":
            return failure("Não é possível excluir: existem lançamentos vinculados a esta categoria (ou às subcategorias do grupo). Altere esses lançamentos antes de excluir.")
        case "PLAN_NOT_EMPTY":
            return failure("Esta empresa já tem categorias, então o plano padrão não foi aplicado. A lista foi atualizada.",
                { refetch: true, closeDialog: true })
        case "NOT_A_GROUP":
            return failure("Subcategorias só podem ser criadas dentro de um grupo. A lista foi atualizada.",
                { refetch: true, closeDialog: true })
        case "SEQUENCE_EXHAUSTED":
            return failure("Não há mais códigos disponíveis neste nível do plano.")
        case "FINANCIAL_CATEGORY_CONFLICT":
            return failure("As categorias foram alteradas ao mesmo tempo por outra pessoa. A lista foi atualizada.",
                { refetch: true, closeDialog: true })
        default:
            break
    }

    switch (error.status) {
        case 400:
            return failure("Confira os dados informados.")
        case 401:
            return failure("Sua sessão terminou. Entre de novo.", { sessionExpired: true, closeDialog: true })
        case 403:
            return failure("Você não tem permissão para esta ação nas categorias desta empresa. Se o seu acesso mudou, recarregue a página.",
                { closeDialog: true })
        case 404:
            return failure("Categoria não encontrada nesta empresa. A lista foi atualizada.", { refetch: true, closeDialog: true })
        case 409:
            return failure("A operação conflita com o estado atual das categorias. A lista foi atualizada.", { refetch: true, closeDialog: true })
        default:
            return failure(GENERIC)
    }
}

interface CategoryQueryClient {
    invalidateQueries: (filters: { queryKey: readonly unknown[] }) => Promise<unknown>
    setQueryData: (key: readonly unknown[], value: null) => unknown
}

/**
 * Aplica os efeitos de uma falha: 401 encerra a sessão; conflitos de
 * versão/estado relêem o catálogo da mesma Company (nunca reenviam nem
 * sobrescrevem). Devolve a descrição para a tela mostrar.
 */
export async function handleFinancialCategoryFailure(
    queryClient: CategoryQueryClient,
    companyId: string,
    error: unknown,
): Promise<FinancialCategoryFailure> {
    const failure = describeFinancialCategoryError(error)

    if (failure.sessionExpired) {
        queryClient.setQueryData(currentUserQueryKey, null)
    } else if (failure.refetch) {
        await queryClient.invalidateQueries({ queryKey: ["companies", companyId, "financial-categories"] })
    }

    return failure
}
