import type { AccountingSourceOption } from "../hooks/use-accounting-sources"

interface SourcePickerProps {
    options: AccountingSourceOption[]
    value: string | null
    onChange: (sourceId: string) => void
}

/** Seletor da fonte contábil: só aparece quando a empresa tem mais de uma fonte visível. */
export function SourcePicker({ options, value, onChange }: SourcePickerProps) {
    if (options.length <= 1) {
        return null
    }

    return (
        <label>
            Fonte{" "}
            <select className="search" value={value ?? ""} onChange={(event) => onChange(event.target.value)}>
                {options.map((option) => (
                    <option key={option.id} value={option.id}>
                        {option.label}
                    </option>
                ))}
            </select>
        </label>
    )
}
