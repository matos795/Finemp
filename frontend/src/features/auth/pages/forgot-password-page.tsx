import { useState, type FormEvent } from "react"
import { Link } from "react-router"
import { requestPasswordReset } from "../api/auth-api"
import { PublicAuthLayout } from "../components/public-auth-layout"
import {
    FORGOT_PASSWORD_NEUTRAL_MESSAGE,
    IDENTITY_PATHS,
    describePasswordResetRequestError,
} from "../lib/identity-flow"
import { loginSchema } from "../schemas/login-schema"

/**
 * /forgot-password — início da recuperação, quando a pessoa ainda não tem
 * link. A resposta é sempre a mesma, exista ou não uma conta com o e-mail.
 */
export function ForgotPasswordPage() {
    const [email, setEmail] = useState("")
    const [fieldError, setFieldError] = useState<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)
    const [sent, setSent] = useState(false)

    async function submit(event: FormEvent) {
        event.preventDefault()
        setError(null)

        const parsed = loginSchema.shape.email.safeParse(email.trim())

        if (!parsed.success) {
            setFieldError(parsed.error.issues[0]?.message ?? "Informe um e-mail válido.")
            return
        }

        setFieldError(null)
        setBusy(true)

        try {
            await requestPasswordReset(parsed.data)
            setEmail("")
            setSent(true)
        } catch (failure) {
            setError(describePasswordResetRequestError(failure))
        } finally {
            setBusy(false)
        }
    }

    return (
        <PublicAuthLayout title="Recuperar senha">
            {sent ? (
                <div style={{ display: "grid", gap: 10 }}>
                    <div className="alert i" role="status">{FORGOT_PASSWORD_NEUTRAL_MESSAGE}</div>
                    <p className="help" style={{ margin: 0 }}>
                        O link vale por tempo limitado. Se não encontrar o e-mail, confira a caixa de spam.
                    </p>
                    <Link className="btn" to={IDENTITY_PATHS.login} style={{ textAlign: "center" }}>
                        Voltar ao login
                    </Link>
                </div>
            ) : (
                <>
                    <p className="help" style={{ marginTop: 0 }}>
                        Informe o e-mail da sua conta para receber um link de recuperação.
                    </p>

                    <form
                        noValidate
                        onSubmit={submit}
                        style={{ display: "grid", gap: 10 }}
                    >
                        <div className="fl">
                            <label htmlFor="email">E-mail</label>
                            <input
                                id="email"
                                type="email"
                                autoComplete="email"
                                placeholder="nome@empresa.com.br"
                                aria-invalid={Boolean(fieldError)}
                                value={email}
                                onChange={(event) => setEmail(event.target.value)}
                            />
                            <div className="err">{fieldError}</div>
                        </div>

                        {error ? (
                            <div className="alert e" role="alert">{error}</div>
                        ) : null}

                        <button
                            type="submit"
                            className="btn"
                            disabled={busy}
                            style={{ width: "100%", padding: "10px 16px" }}
                        >
                            {busy ? "Enviando..." : "Enviar link de recuperação"}
                        </button>

                        <Link to={IDENTITY_PATHS.login} style={{ justifySelf: "center", fontSize: 13 }}>
                            Voltar ao login
                        </Link>
                    </form>
                </>
            )}
        </PublicAuthLayout>
    )
}
