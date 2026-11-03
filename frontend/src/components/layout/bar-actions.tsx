import {
    useContext,
    type ReactNode,
} from "react"
import { createPortal } from "react-dom"
import { BarActionsContext } from "./bar-actions-context"

/**
 * Ações próprias da tela, exibidas na barra superior antes dos ícones
 * fixos, como no protótipo (ex.: "Abrir tudo" / "Fechar tudo" na DRE).
 */
export function BarActions({
    children,
}: {
    children: ReactNode
}) {
    const slot = useContext(BarActionsContext)

    return slot ? createPortal(children, slot) : null
}
