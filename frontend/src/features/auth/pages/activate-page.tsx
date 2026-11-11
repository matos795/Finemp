import { useState } from "react"
import { useNavigate } from "react-router"
import { useQueryClient } from "@tanstack/react-query"
import { activateAccount } from "../api/auth-api"
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
 * /activate#token=… — ativação inicial de uma identidade convidada: a
 * pessoa define a senha e depois entra pelo login normal.
 */
export function ActivatePage() {
    const token = useAccountActionToken()
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const [error, setError] = useState<string | null>(null)
    const [linkRejected, setLinkRejected] = useState(false)

    async function activate(newPassword: string) {
        if (!token) {
            return
        }

        setError(null)

        try {
            await activateAccount(token, newPassword)
            // O backend encerra qualquer sessão aberta ao ativar.
            queryClient.setQueryData(currentUserQueryKey, null)
            const { to, options } = accountActionSuccessNavigation("activate")
            navigate(to, options)
        } catch (failure) {
            const message = describeAccountActionError(failure)
            setLinkRejected(message === INVALID_LINK_MESSAGE)
            setError(message)
        }
    }

    return (
        <PublicAuthLayout title="Ative seu acesso">
            {!token || linkRejected ? (
                <InvalidAccountLink next="invitation" />
            ) : (
                <>
                    <p className="help" style={{ marginTop: 0 }}>
                        Você foi convidado para acessar o FINEMP. Defina sua senha
                        para ativar o acesso; depois, entre com seu e-mail e essa senha.
                    </p>

                    <NewPasswordForm
                        submitLabel="Criar senha e ativar"
                        onSubmit={activate}
                        error={error}
                    />
                </>
            )}
        </PublicAuthLayout>
    )
}
