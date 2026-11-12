import { ApiError } from "../../../lib/api/api-client.ts"
import type { CompanyPermission } from "../../auth/types/auth.types"

type HasPermission = (permission: CompanyPermission) => boolean

export function canReadPlanning(hasPermission: HasPermission) {
    return hasPermission("PLANNING_VIEW")
        || hasPermission("PLANNING_MANAGE")
}

export function canManagePlanning(hasPermission: HasPermission) {
    return hasPermission("PLANNING_MANAGE")
}

export function describePlanningMutationError(error: unknown) {
    if (error instanceof ApiError) {
        if (error.status === 409 || error.code === "STALE_VERSION") {
            return "Este registro foi atualizado por outra operação. Atualize a tela antes de tentar novamente."
        }

        if (error.status === 403) {
            return "Seu acesso não possui permissão para alterar o planejamento."
        }

        if (error.status === 400) {
            return "Confira os campos informados."
        }
    }

    return "Não foi possível concluir a alteração."
}


export function reconcilePlanningPage(
    currentPage: number,
    totalPages: number,
    itemCount: number,
) {
    if (
        currentPage > 0 &&
        itemCount === 0 &&
        currentPage >= totalPages
    ) {
        return Math.max(0, totalPages - 1)
    }

    return currentPage
}
