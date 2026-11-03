import { useForm } from "react-hook-form"
import { useQueryClient } from "@tanstack/react-query"
import { Navigate, useNavigate } from "react-router"
import { useState } from "react"
import { currentUserQueryKey, useCurrentUser } from "../hooks/use-current-user"
import { loginResolver, type LoginFormData } from "../schemas/login-schema"
import { getCurrentUser, login } from "../api/auth-api"
import logoUrl from "../../../assets/finemp-logo.png"



export function LoginPage() {
    const navigate = useNavigate()
    const queryClient = useQueryClient()
    const currentUser = useCurrentUser()

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
        <main
            className="fin min-h-screen"
            style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                padding: "24px var(--g)",
            }}
        >
            <div className="mbox" style={{ width: "min(420px, 100%)" }}>
                <div style={{ display: "flex", justifyContent: "center", marginBottom: 14 }}>
                    <img
                        src={logoUrl}
                        alt="FINEMP Finanças Empresariais"
                        style={{ height: 60, width: "auto" }}
                    />
                </div>

                <h2>Acesse sua conta</h2>

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
                </form>
            </div>
        </main>
    )
}
