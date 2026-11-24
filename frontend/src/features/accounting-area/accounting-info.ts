import { createContext, useContext, type ReactNode } from "react"

/** infoAbrir do protótipo: abre #mInfo com o título do quadro e os textos explicativos. */
export const AccountingInfoContext = createContext<(title: string, body: ReactNode) => void>(() => {})

export function useAccountingInfo() {
    return useContext(AccountingInfoContext)
}
