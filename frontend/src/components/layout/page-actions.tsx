/**
 * Ações da barra do protótipo homologado (.barra-acoes).
 *
 * No protótipo, "+ Entrada", "+ Saída" e "Transferência" só aparecem em
 * Lançamentos, tela ainda não ligada. Tutorial e alertas dependem de
 * conteúdo ainda não portado e ficam inativos (D5).
 */
export function PageActions({
    onSlotChange,
}: {
    /** Recebe o espaço onde a tela atual coloca suas próprias ações. */
    onSlotChange: (slot: HTMLElement | null) => void
}) {
    return (
        <div className="barra-acoes">
            <span
                ref={onSlotChange}
                style={{ display: "contents" }}
            />

            <a
                className="icobtn zap"
                href="https://wa.me/551150266712"
                target="_blank"
                rel="noopener noreferrer"
                title="Suporte FINEMP no WhatsApp – (11) 5026-6712"
                aria-label="Falar com o suporte FINEMP no WhatsApp"
            >
                <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                >
                    <path
                        fill="currentColor"
                        d="M12 2a10 10 0 00-8.6 15.1L2 22l5-1.3A10 10 0 1012 2zm0 18.2a8.2 8.2 0 01-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1112 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8s-.4-.1-.6.1-.7.8-.8 1-.3.2-.5.1a6.7 6.7 0 01-3.3-2.9c-.3-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 00-.7.3 3 3 0 00-.9 2.2 5.2 5.2 0 001.1 2.7 11.8 11.8 0 004.5 4c1.7.7 2.3.8 3.2.6a2.7 2.7 0 001.8-1.3 2.2 2.2 0 00.2-1.3c-.1-.1-.3-.2-.5-.3z"
                    />
                </svg>
            </a>

            <button
                type="button"
                className="icobtn"
                disabled
                title="Tutorial desta tela (em breve)"
                aria-label="Assistir ao tutorial desta tela"
            >
                <svg
                    width="20"
                    height="20"
                    viewBox="0 0 20 20"
                    aria-hidden="true"
                >
                    <circle
                        cx="10"
                        cy="10"
                        r="7.5"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.7"
                    />
                    <path
                        d="M8.3 6.9v6.2l5-3.1z"
                        fill="currentColor"
                    />
                </svg>
            </button>

            <button
                type="button"
                className="icobtn"
                disabled
                title="Alertas (em breve)"
                aria-label="Alertas"
            >
                <svg
                    width="20"
                    height="20"
                    viewBox="0 0 20 20"
                    aria-hidden="true"
                >
                    <path
                        d="M10 2.5a5 5 0 00-5 5v3.2L3.6 13.5h12.8L15 10.7V7.5a5 5 0 00-5-5zM8 15.5a2 2 0 004 0"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.7"
                        strokeLinejoin="round"
                    />
                </svg>
            </button>
        </div>
    )
}
