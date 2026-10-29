import { ApiError } from "./api-client"

/** 401: sessão ausente ou expirada; a tela volta ao login. */
export function isSessionExpired(error: unknown) {
    return error instanceof ApiError && error.status === 401
}

export function apiErrorStatus(error: unknown) {
    return error instanceof ApiError ? error.status : undefined
}
