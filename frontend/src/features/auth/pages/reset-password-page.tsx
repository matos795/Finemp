import { useState } from "react"
import { useNavigate } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import { completePasswordReset } from "../api/auth-api"
import { InvalidAccountLink } from "../components/invalid-account-link"
import { NewPasswordForm } from "../components/new-password-form"
import { PublicAuthLayout } from "../components/public-auth-layout"
import { useAccountActionToken } from "../hooks/use-account-action-token"
import { currentUserQueryKey } from "../hooks/use-current-user"
import {
    INVALID_LINK_MESSAGE,
    accountActionSuccessNavigation,
    describeAccountActionError,
} from "../lib/identity-flow"

/**
 * /reset-password#token=… — conclusão da recuperação: define a nova senha
 * a partir do link recebido por e-mail.
 */
export function ResetPasswordPage() {
    const token = useAccountActionToken()
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [error, setError] = useState<string | null>(null)
    const [linkRejected, setLinkRejected] = useState(false)

    async function reset(newPassword: string) {
        if (!token) {
            return
        }

        setError(null)

        try {
            await completePasswordReset(token, newPassword)
            // O backend encerra qualquer sessão aberta ao redefinir.
            queryClient.setQueryData(currentUserQueryKey, null)
            const { to, options } = accountActionSuccessNavigation("reset")
            navigate(to, options)
        } catch (failure) {
            const message = describeAccountActionError(failure)
            setLinkRejected(message === INVALID_LINK_MESSAGE)
            setError(message)
        }
    }

    return (
        <PublicAuthLayout title="Crie uma nova senha">
            {!token || linkRejected ? (
                <InvalidAccountLink next="reset" />
            ) : (
                <>
                    <p className="help" style={{ marginTop: 0 }}>
                        Defina a nova senha da sua conta. Depois, entre novamente com ela.
                    </p>

                    <NewPasswordForm
                        submitLabel="Redefinir senha"
                        onSubmit={reset}
                        error={error}
                    />
                </>
            )}
        </PublicAuthLayout>
    )
}
