import { ApiError, apiFetch, apiRequest } from "../../../lib/api/api-client.ts"
import type { CurrentUser, LoginRequest } from "../types/auth.types.ts"


export async function login(
    credentials: LoginRequest,
): Promise<void> {
    await apiRequest<void>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify(credentials),
    })
}

export async function logout(): Promise<void> {
    await apiRequest<void>("/api/auth/logout", {
        method: "POST",
    })
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
    try {
        return await apiRequest<CurrentUser>("/api/auth/me")
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            return null
        }

        throw error
    }
}

/**
 * Convite: define a senha inicial. `token` vem do fragmento do link e vai
 * só no corpo, nunca na URL. O backend encerra qualquer sessão ao concluir.
 */
export async function activateAccount(token: string, newPassword: string): Promise<void> {
    await apiRequest<void>("/api/auth/activate", {
        method: "POST",
        body: JSON.stringify({ token, newPassword }),
    })
}

/**
 * Pede o e-mail de recuperação. O backend responde 202 sem corpo para
 * qualquer conta (ou nenhuma); apiFetch usa o mesmo caminho de CSRF e
 * cookies de apiRequest, sem tentar ler JSON de uma resposta vazia.
 */
export async function requestPasswordReset(email: string): Promise<void> {
    await apiFetch("/api/auth/password-reset/request", {
        method: "POST",
        body: JSON.stringify({ email }),
    })
}

/** Recuperação: define a nova senha a partir do token do link. */
export async function completePasswordReset(token: string, newPassword: string): Promise<void> {
    await apiRequest<void>("/api/auth/password-reset/complete", {
        method: "POST",
        body: JSON.stringify({ token, newPassword }),
    })
}
