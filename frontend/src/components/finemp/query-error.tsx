import { useEffect } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { ApiError } from "../../lib/api/api-client"
import { currentUserQueryKey } from "../../features/auth/hooks/use-current-user"
import { isSessionExpired } from "../../lib/api/api-errors"

interface QueryErrorProps {
    error: unknown
    /** Mensagens por status; o restante usa a mensagem neutra. */
    messages?: Partial<Record<number, string>>
    fallback: string
    onRetry?: () => void
}

/**
 * Erro de consulta no padrão do protótipo (.alert.e).
 * 401 volta ao login existente; 403/404 e demais mostram mensagem
 * neutra, sem detalhes internos.
 */
export function QueryError({
    error,
    messages,
    fallback,
    onRetry,
}: QueryErrorProps) {
    const queryClient = useQueryClient()
    const sessionExpired = isSessionExpired(error)

    useEffect(() => {
        if (sessionExpired) {
            queryClient.setQueryData(
                currentUserQueryKey,
                null,
            )
        }
    }, [sessionExpired, queryClient])

    if (sessionExpired) {
        return null
    }

    const message =
        (error instanceof ApiError
            ? messages?.[error.status]
            : undefined) ?? fallback

    return (
        <div
            className="alert e"
            role="alert"
            style={{
                display: "flex",
                flexWrap: "wrap",
                gap: 12,
                alignItems: "center",
                justifyContent: "space-between",
            }}
        >
            <span>{message}</span>

            {onRetry && (
                <button
                    type="button"
                    className="btn sec sm"
                    onClick={onRetry}
                >
                    Tentar novamente
                </button>
            )}
        </div>
    )
}
