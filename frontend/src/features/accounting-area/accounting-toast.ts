import { createContext, useContext } from "react"

export type ToastKind = "" | "ok" | "err"

/** toast(msg, kind) do protótipo. */
export const AccountingToastContext = createContext<(message: string, kind?: ToastKind) => void>(() => {})

export function useAccountingToast() {
    return useContext(AccountingToastContext)
}
