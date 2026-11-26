export interface DreLine {
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
    total: number
}

export interface DreResult {
    lines: DreLine[]
    unclassifiedAccountCount: number
    /** Cobertura acumulada de MOVEMENTS por fonte (#500/#509); ausente em respostas antigas. */
    coverage?: {
        accountingSourceId: string
        importedFrom: string | null
        importedTo: string | null
        intervals: { importedFrom: string; importedTo: string }[]
    }[]
}

export interface DrePeriod {
    startDate: string
    endDate: string
}