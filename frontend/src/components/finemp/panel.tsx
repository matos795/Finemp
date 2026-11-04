import {
    useEffect,
    useRef,
    useState,
    type ReactNode,
} from "react"
import { createPortal } from "react-dom"

interface PanelProps {
    title?: ReactNode
    /**
     * Texto explicativo do quadro. No protótipo homologado ele vira o
     * botão "i" ao lado do título, que abre um balão (.info-btn/.info-pop).
     */
    help?: ReactNode
    children?: ReactNode
    className?: string
}

const POPOVER_MAX_WIDTH = 460

function InfoButton({
    help,
}: {
    help: ReactNode
}) {
    const buttonRef = useRef<HTMLButtonElement>(null)
    const popoverRef = useRef<HTMLDivElement>(null)

    const [position, setPosition] = useState<{
        left: number
        top: number
        width: number
        container: Element
    } | null>(null)

    function open() {
        const rect = buttonRef.current?.getBoundingClientRect()

        if (!rect) {
            return
        }

        const width = Math.min(
            POPOVER_MAX_WIDTH,
            window.innerWidth - 24,
        )

        setPosition({
            // Fora do título, como no protótipo (anexado ao corpo da página).
            container: buttonRef.current?.closest(".fin") ?? document.body,
            width,
            left: Math.max(
                12,
                Math.min(rect.left - 12, window.innerWidth - width - 12),
            ),
            top: rect.bottom + 8,
        })
    }

    useEffect(() => {
        if (!position) {
            return
        }

        function close(event: Event) {
            const target = event.target as Node

            if (
                buttonRef.current?.contains(target) ||
                popoverRef.current?.contains(target)
            ) {
                return
            }

            setPosition(null)
        }

        function closeOnEscape(event: KeyboardEvent) {
            if (event.key === "Escape") {
                setPosition(null)
            }
        }

        document.addEventListener("click", close, true)
        document.addEventListener("keydown", closeOnEscape)
        window.addEventListener("resize", close)

        return () => {
            document.removeEventListener("click", close, true)
            document.removeEventListener("keydown", closeOnEscape)
            window.removeEventListener("resize", close)
        }
    }, [position])

    return (
        <>
            <button
                ref={buttonRef}
                type="button"
                className={position ? "info-btn on" : "info-btn"}
                aria-label="Informações sobre este quadro"
                aria-expanded={Boolean(position)}
                onClick={() => (position ? setPosition(null) : open())}
            >
                i
            </button>

            {position && createPortal(
                <div
                    ref={popoverRef}
                    className="info-pop"
                    role="note"
                    style={{
                        left: position.left,
                        top: position.top,
                        width: position.width,
                        maxHeight: `calc(100vh - ${Math.round(position.top + 16)}px)`,
                    }}
                >
                    {help}
                </div>,
                position.container,
            )}
        </>
    )
}

/** Painel do protótipo homologado (.panel com h2 dourado). */
export function Panel({
    title,
    help,
    children,
    className,
}: PanelProps) {
    return (
        <div className={className ? `panel ${className}` : "panel"}>
            {title && (
                <h2>
                    {title}
                    {help && <InfoButton help={help} />}
                </h2>
            )}

            {!title && help && <p className="help">{help}</p>}

            {children}
        </div>
    )
}
