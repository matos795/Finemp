/** Campo "Nome" compartilhado pelos diálogos de criar e renomear. */
export function CategoryNameField({ id, value, error, onChange, autoFocus = true }: {
    id: string
    value: string
    error?: string
    onChange: (value: string) => void
    autoFocus?: boolean
}) {
    return (
        <div className="fl full">
            <label htmlFor={id}>Nome</label>
            <input
                type="text"
                id={id}
                maxLength={255}
                autoFocus={autoFocus}
                aria-invalid={Boolean(error)}
                value={value}
                onChange={(event) => onChange(event.target.value)}
            />
            {error ? <div className="err">{error}</div> : null}
        </div>
    )
}
