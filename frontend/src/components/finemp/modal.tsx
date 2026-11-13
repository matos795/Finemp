import {
    useEffect,
    type ReactNode,
} from "react"
import { createPortal } from "react-dom"

interface ModalProps {
    /** Largura máxima da caixa, como o genAbrir(html, largura) do protótipo. */
    width?: number
    labelledBy?: string
    /** Esc fecha, exceto durante uma operação. */
    onClose?: () => void
    children: ReactNode
}

/** Janela do protótipo homologado (.modal > .mbox). */
export function Modal({
    width = 760,
    labelledBy,
    onClose,
    children,
}: ModalProps) {
    useEffect(() => {
        if (!onClose) {
            return
        }

        const close = onClose

        function closeOnEscape(event: KeyboardEvent) {
            if (event.key === "Escape") {
                close()
            }
        }

        document.addEventListener("keydown", closeOnEscape)

        return () => document.removeEventListener("keydown", closeOnEscape)
    }, [onClose])

    const modal = (
        <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby={labelledBy}
        >
            <div
                className="mbox"
                style={{ width: `min(${width}px, 100%)` }}
            >
                {children}
            </div>
        </div>
    )

    const shell = document.querySelector<HTMLElement>(".fin")
    return shell ? createPortal(modal, shell) : modal
}
