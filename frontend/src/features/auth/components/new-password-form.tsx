import { useState, type FormEvent } from "react"
import {
    PASSWORD_RULES_HINT,
    validateNewPassword,
    type NewPasswordErrors,
} from "../lib/account-password-policy"

interface NewPasswordFormProps {
    submitLabel: string
    /** Recebe a senha já conferida localmente; a página chama a API. */
    onSubmit: (newPassword: string) => Promise<void>
    /** Erro da API, já traduzido pela página (nunca com token). */
    error: string | null
}

/**
 * "Nova senha" + "Confirmar senha" das telas de ativação e de redefinição.
 * Só confere a política localmente (UX); o backend decide.
 */
export function NewPasswordForm({ submitLabel, onSubmit, error }: NewPasswordFormProps) {
    const [password, setPassword] = useState("")
    const [confirmation, setConfirmation] = useState("")
    const [errors, setErrors] = useState<NewPasswordErrors>({})
    const [busy, setBusy] = useState(false)

    async function submit(event: FormEvent) {
        event.preventDefault()

        const found = validateNewPassword(password, confirmation)
        setErrors(found)

        if (Object.keys(found).length > 0 || busy) {
            return
        }

        setBusy(true)

        try {
            await onSubmit(password)
        } finally {
            setBusy(false)
        }
    }

    return (
        <form
            noValidate
            onSubmit={submit}
            style={{ display: "grid", gap: 10 }}
        >
            <div className="fl">
                <label htmlFor="newPassword">Nova senha</label>
                <input
                    id="newPassword"
                    type="password"
                    autoComplete="new-password"
                    aria-invalid={Boolean(errors.password)}
                    aria-describedby="newPasswordHint"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                />
                <div id="newPasswordHint" className="help" style={{ margin: 0 }}>{PASSWORD_RULES_HINT}</div>
                <div className="err">{errors.password}</div>
            </div>

            <div className="fl">
                <label htmlFor="confirmPassword">Confirmar senha</label>
                <input
                    id="confirmPassword"
                    type="password"
                    autoComplete="new-password"
                    aria-invalid={Boolean(errors.confirmation)}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                />
                <div className="err">{errors.confirmation}</div>
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
                {busy ? "Enviando..." : submitLabel}
            </button>
        </form>
    )
}
