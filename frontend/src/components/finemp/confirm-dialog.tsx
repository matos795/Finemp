import { useEffect } from "react"

interface ConfirmDialogProps {
    title: string
    message: string
    confirmLabel?: string
    busy?: boolean
    onConfirm: () => void
    onCancel: () => void
}

/** Modal de confirmação do protótipo (#mConf: .modal > .mbox). */
export function ConfirmDialog({
    title,
    message,
    confirmLabel = "Confirmar",
    busy = false,
    onConfirm,
    onCancel,
}: ConfirmDialogProps) {
    useEffect(() => {
        function closeOnEscape(event: KeyboardEvent) {
            if (event.key === "Escape" && !busy) {
                onCancel()
            }
        }

        document.addEventListener("keydown", closeOnEscape)

        return () => document.removeEventListener("keydown", closeOnEscape)
    }, [busy, onCancel])

    return (
        <div
            className="modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
        >
            <div
                className="mbox"
                style={{ width: "min(460px, 100%)" }}
            >
                <h2 id="confirm-title">{title}</h2>
                <p>{message}</p>

                <div className="mact">
                    <button
                        type="button"
                        className="btn sec"
                        disabled={busy}
                        onClick={onCancel}
                        autoFocus
                    >
                        Cancelar
                    </button>

                    <button
                        type="button"
                        className="btn danger"
                        disabled={busy}
                        onClick={onConfirm}
                    >
                        {confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    )
}
