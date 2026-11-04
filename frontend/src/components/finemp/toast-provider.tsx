import {
    useCallback,
    useRef,
    useState,
    type ReactNode,
} from "react"
import { ToastContext } from "./toast-context"

interface ToastItem {
    id: number
    message: string
    isError: boolean
}

/** No protótipo: até 3 avisos (#toasts), cada um some em 4,5 s. */
const MAX_TOASTS = 3
const TOAST_DURATION_MS = 4500

export function ToastProvider({
    children,
}: {
    children: ReactNode
}) {
    const [toasts, setToasts] = useState<ToastItem[]>([])
    const nextId = useRef(0)

    const showToast = useCallback((message: string, isError = false) => {
        const id = nextId.current++

        setToasts((current) => [
            ...current.slice(-(MAX_TOASTS - 1)),
            { id, message, isError },
        ])

        window.setTimeout(() => {
            setToasts((current) =>
                current.filter((toast) => toast.id !== id),
            )
        }, TOAST_DURATION_MS)
    }, [])

    return (
        <ToastContext.Provider value={showToast}>
            {children}

            <div
                id="toasts"
                aria-live="polite"
            >
                {toasts.map((toast) => (
                    <div
                        key={toast.id}
                        className={toast.isError ? "toast e" : "toast"}
                        role={toast.isError ? "alert" : "status"}
                    >
                        {toast.message}
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    )
}
