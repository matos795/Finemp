interface PagerProps {
    /** Página atual, começando em 0 (contrato da API). */
    page: number
    totalPages: number
    disabled?: boolean
    onChange: (page: number) => void
}

/**
 * Paginação do servidor com os botões do protótipo (.btn.sec.sm).
 * Só aparece quando há mais de uma página.
 */
export function Pager({
    page,
    totalPages,
    disabled = false,
    onChange,
}: PagerProps) {
    if (totalPages <= 1) {
        return null
    }

    return (
        <div className="mact">
            <span
                className="mut"
                style={{ alignSelf: "center", fontSize: 13 }}
            >
                Página {page + 1} de {totalPages}
            </span>

            <button
                type="button"
                className="btn sec sm"
                disabled={disabled || page === 0}
                onClick={() => onChange(page - 1)}
            >
                Anterior
            </button>

            <button
                type="button"
                className="btn sec sm"
                disabled={disabled || page + 1 >= totalPages}
                onClick={() => onChange(page + 1)}
            >
                Próxima
            </button>
        </div>
    )
}
