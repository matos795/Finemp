import { useCallback, useRef, useState, type ReactNode } from "react"
import { AccountingToastContext, type ToastKind } from "../accounting-toast"

interface Toast {
    id: number
    message: string
    kind: ToastKind
}

/** Caixa .toasts do protótipo: canto inferior direito, borda colorida por tipo. */
export function AccountingToasts({ children }: { children: ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([])
    const sequence = useRef(0)

    const show = useCallback((message: string, kind: ToastKind = "") => {
        const id = ++sequence.current

        setToasts((current) => [...current, { id, message, kind }])
        window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4000)
    }, [])

    return (
        <AccountingToastContext.Provider value={show}>
            {children}
            <div className="toasts" id="toasts" aria-live="polite">
                {toasts.map((toast) => (
                    <div key={toast.id} className={toast.kind ? `toast ${toast.kind}` : "toast"}>
                        {toast.message}
                    </div>
                ))}
            </div>
        </AccountingToastContext.Provider>
    )
}
