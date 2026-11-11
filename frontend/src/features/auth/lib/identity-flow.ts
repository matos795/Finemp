import { ApiError } from "../../../lib/api/api-client.ts"

/** Rotas públicas de identidade (fora de ProtectedApp). */
export const IDENTITY_PATHS = {
    login: "/login",
    activate: "/activate",
    forgotPassword: "/forgot-password",
    resetPassword: "/reset-password",
} as const

export const PUBLIC_ROUTE_PATHS: readonly string[] = Object.values(IDENTITY_PATHS)

export const INVALID_LINK_MESSAGE = "Este link é inválido, expirou ou já foi utilizado."
export const TOO_MANY_ATTEMPTS_MESSAGE = "Muitas tentativas. Tente novamente mais tarde."
const UNEXPECTED_MESSAGE = "Não foi possível concluir agora. Tente novamente em instantes."

/** Resposta da recuperação: igual para conta existente, inexistente ou inativa. */
export const FORGOT_PASSWORD_NEUTRAL_MESSAGE =
    "Se houver uma conta associada a esse e-mail, enviaremos as instruções de recuperação."

/**
 * Ativação e conclusão da recuperação. O backend responde 400 tanto para
 * token inválido, expirado ou já usado quanto para senha recusada; a senha
 * já foi conferida aqui com a mesma política, então a mensagem trata o
 * link. Nunca repete token, status ou corpo da API.
 */
export function describeAccountActionError(error: unknown) {
    if (error instanceof ApiError && error.status === 400) {
        return INVALID_LINK_MESSAGE
    }

    if (error instanceof ApiError && error.status === 429) {
        return TOO_MANY_ATTEMPTS_MESSAGE
    }

    return UNEXPECTED_MESSAGE
}

/**
 * Pedido de recuperação. O backend sempre responde 202 (inclusive ao
 * limitar tentativas), então só formato inválido, 429 ou falha técnica
 * chegam aqui; nenhuma mensagem fala da existência da conta.
 */
export function describePasswordResetRequestError(error: unknown) {
    if (error instanceof ApiError && error.status === 400) {
        return "Confira o e-mail informado."
    }

    if (error instanceof ApiError && error.status === 429) {
        return TOO_MANY_ATTEMPTS_MESSAGE
    }

    return UNEXPECTED_MESSAGE
}

const LOGIN_NOTICES = {
    activated: "Acesso ativado. Entre com seu e-mail e a senha que você criou.",
    "password-reset": "Senha alterada. Entre novamente com sua nova senha.",
} as const

type LoginNotice = keyof typeof LOGIN_NOTICES

/**
 * Para onde ir depois de ativar ou redefinir: /login, sem query string,
 * com um código fixo de aviso no estado de navegação (nunca o token).
 */
export function accountActionSuccessNavigation(action: "activate" | "reset") {
    const identityNotice: LoginNotice = action === "activate" ? "activated" : "password-reset"

    return {
        to: IDENTITY_PATHS.login,
        options: { replace: true, state: { identityNotice } },
    }
}

/** Aviso do login a partir do estado de navegação; só códigos conhecidos. */
export function loginNoticeFrom(state: unknown): string | null {
    if (!state || typeof state !== "object" || !("identityNotice" in state)) {
        return null
    }

    const notice = state.identityNotice

    return typeof notice === "string" && Object.hasOwn(LOGIN_NOTICES, notice)
        ? LOGIN_NOTICES[notice as LoginNotice]
        : null
}
