import type {
    CsvSeparator,
    StatementFileFormat,
} from "../types/statement.types"

/** Extensões aceitas: OFX/QFX e CSV/TXT. Excel fica de fora (PA2). */
export const STATEMENT_FILE_ACCEPT = ".ofx,.qfx,.csv,.txt"

export function statementFormatOf(
    fileName: string,
): StatementFileFormat | null {
    const extension = fileName.toLowerCase().split(".").pop() ?? ""

    if (extension === "ofx" || extension === "qfx") {
        return "OFX"
    }

    if (extension === "csv" || extension === "txt") {
        return "CSV"
    }

    return null
}

/** O backend exige separador explícito no CSV; não adivinha. */
export const csvSeparators: {
    value: CsvSeparator
    label: string
}[] = [
    { value: "SEMICOLON", label: "Ponto e vírgula (;)" },
    { value: "COMMA", label: "Vírgula (,)" },
    { value: "TAB", label: "Tabulação" },
    { value: "PIPE", label: "Barra vertical (|)" },
]
