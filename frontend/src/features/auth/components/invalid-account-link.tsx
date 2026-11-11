import { Link } from "react-router"
import { IDENTITY_PATHS, INVALID_LINK_MESSAGE } from "../lib/identity-flow"

/** Link sem token válido (ausente, malformado, expirado ou já usado). */
export function InvalidAccountLink({ next }: { next: "invitation" | "reset" }) {
    return (
        <div style={{ display: "grid", gap: 10 }}>
            <div className="alert e" role="alert">{INVALID_LINK_MESSAGE}</div>

            <p className="help" style={{ margin: 0 }}>
                {next === "invitation"
                    ? "Peça um novo convite ao administrador do seu escritório."
                    : "Você pode solicitar um novo link de recuperação."}
            </p>

            {next === "reset" ? (
                <Link className="btn" to={IDENTITY_PATHS.forgotPassword} style={{ textAlign: "center" }}>
                    Solicitar novo link
                </Link>
            ) : null}

            <Link className="btn sec" to={IDENTITY_PATHS.login} style={{ textAlign: "center" }}>
                Ir para o login
            </Link>
        </div>
    )
}
