import { useForm } from "react-hook-form"
import { useQueryClient } from "@tanstack/react-query"
import { Link, Navigate, useLocation, useNavigate } from "react-router"
import { useEffect, useState } from "react"
import { currentUserQueryKey, useCurrentUser } from "../hooks/use-current-user"
import { loginResolver, type LoginFormData } from "../schemas/login-schema"
import { getCurrentUser, login } from "../api/auth-api"
import { PublicAuthLayout } from "../components/public-auth-layout"
import { IDENTITY_PATHS, loginNoticeFrom } from "../lib/identity-flow"

export function LoginPage() {
    const navigate = useNavigate()
    const location = useLocation()
    const queryClient = useQueryClient()
    const currentUser = useCurrentUser()

    // Aviso fixo vindo da ativação/redefinição (estado de navegação, nunca
    // query string). Depois de lido, sai do histórico para não reaparecer.
    const [notice] = useState(() => loginNoticeFrom(location.state))

    useEffect(() => {
        if (location.state) {
            navigate(location.pathname, { replace: true, state: null })
        }
    }, [location.state, location.pathname, navigate])

    const [authenticationError, setAuthenticationError] =
        useState<string | null>(null)

    const form = useForm<LoginFormData>({
        resolver: loginResolver,
        defaultValues: {
            email: "",
            password: "",
        },
    })

    if (currentUser.data) {
        return <Navigate to="/" replace />
    }

    async function onSubmit(data: LoginFormData) {
        try {
            setAuthenticationError(null)

            await login(data)

            const user = await getCurrentUser()

            queryClient.setQueryData(
                currentUserQueryKey,
                user,
            )

            navigate("/", {
                replace: true,
            })
        } catch {
            setAuthenticationError(
                "Não foi possível entrar. Confira seu e-mail e senha.",
            )
        }
    }

    const errors = form.formState.errors

    // PA8: a tela de login da referência homologada não está no HTML do
    // projeto; usa-se a caixa (.mbox), os campos (.fl) e o botão (.btn)
    // do protótipo, sem layout novo.
    return (
        <PublicAuthLayout title="Acesse sua conta">
            {notice && (
                <div className="alert i" role="status">
                    {notice}
                </div>
            )}

            <p className="help" style={{ marginTop: 0 }}>
                Entre com suas credenciais para acessar as empresas e
                informações disponíveis para o seu perfil.
            </p>

            <form
                noValidate
                onSubmit={form.handleSubmit(onSubmit)}
                style={{ display: "grid", gap: 10 }}
            >
                <div className="fl">
                    <label htmlFor="email">E-mail</label>

                    <input
                        id="email"
                        type="email"
                        autoComplete="email"
                        placeholder="nome@empresa.com.br"
                        aria-invalid={Boolean(errors.email)}
                        {...form.register("email")}
                    />

                    <div className="err">{errors.email?.message}</div>
                </div>

                <div className="fl">
                    <label htmlFor="password">Senha</label>

                    <input
                        id="password"
                        type="password"
                        autoComplete="current-password"
                        aria-invalid={Boolean(errors.password)}
                        {...form.register("password")}
                    />

                    <div className="err">{errors.password?.message}</div>
                </div>

                {authenticationError && (
                    <div className="alert e" role="alert">
                        {authenticationError}
                    </div>
                )}

                <button
                    type="submit"
                    className="btn"
                    disabled={form.formState.isSubmitting}
                    style={{ width: "100%", padding: "10px 16px" }}
                >
                    {form.formState.isSubmitting
                        ? "Entrando..."
                        : "Entrar"}
                </button>

                <Link
                    to={IDENTITY_PATHS.forgotPassword}
                    style={{ justifySelf: "center", fontSize: 13 }}
                >
                    Esqueci minha senha
                </Link>
            </form>
        </PublicAuthLayout>
    )
}
