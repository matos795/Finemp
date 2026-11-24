import { useCallback, useEffect, useState, type ReactNode } from "react"
import { AccountingInfoContext, useAccountingInfo } from "../accounting-info"

/**
 * No protótipo os textos explicativos (.sub) saem da tela e ficam no (i) de
 * cada quadro (infoMontar/infoAbrir). Este provider renderiza o #mInfo.
 */
export function AccountingInfoProvider({ children }: { children: ReactNode }) {
    const [info, setInfo] = useState<{ title: string; body: ReactNode } | null>(null)
    const open = useCallback((title: string, body: ReactNode) => setInfo({ title, body }), [])

    useEffect(() => {
        if (!info) return

        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") setInfo(null)
        }

        window.addEventListener("keydown", onKey)

        return () => window.removeEventListener("keydown", onKey)
    }, [info])

    return (
        <AccountingInfoContext.Provider value={open}>
            {children}
            <div
                className="modal"
                id="mInfo"
                hidden={!info}
                role="dialog"
                aria-modal="true"
                aria-labelledby="mInfoTit"
                onClick={(event) => {
                    if (event.target === event.currentTarget) setInfo(null)
                }}
            >
                <div className="dlg">
                    <h2 id="mInfoTit">{info?.title ?? "Sobre este quadro"}</h2>
                    <div className="info-body" id="mInfoBody">
                        {info?.body}
                    </div>
                    <div className="actions">
                        <button className="btn primary" id="mInfoOk" type="button" onClick={() => setInfo(null)}>
                            Fechar
                        </button>
                    </div>
                </div>
            </div>
        </AccountingInfoContext.Provider>
    )
}

/** Botão (i) do protótipo (.ibtn[data-own]) ao lado do título do quadro. */
export function InfoButton({ title, children }: { title: string; children: ReactNode }) {
    const openInfo = useAccountingInfo()

    return (
        <button
            type="button"
            className="ibtn"
            data-own="1"
            title="Como ler este quadro"
            aria-label={`Informações sobre ${title}`}
            onClick={() => openInfo(title, children)}
        >
            i
        </button>
    )
}
