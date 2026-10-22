import { ApiError, apiRequest } from "../../../lib/api/api-client"
import type { CurrentUser, LoginRequest } from "../types/auth.types"


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