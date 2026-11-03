import {
    createContext,
    useContext,
} from "react"

export type ShowToast = (message: string, isError?: boolean) => void

export const ToastContext =
    createContext<ShowToast | null>(null)

/** Aviso rápido no canto da tela, como o toast() do protótipo. */
export function useToast() {
    const showToast = useContext(ToastContext)

    if (!showToast) {
        throw new Error("useToast must be used inside ToastProvider")
    }

    return showToast
}
