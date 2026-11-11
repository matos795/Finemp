import type { ReactNode } from "react"
import logoUrl from "../../../assets/finemp-logo.png"

/**
 * Moldura das telas públicas de autenticação (login, ativação e
 * recuperação): a caixa (.mbox) e o logo do protótipo, sem layout novo.
 */
export function PublicAuthLayout({ title, children }: { title: string, children: ReactNode }) {
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

                <h2>{title}</h2>

                {children}
            </div>
        </main>
    )
}
