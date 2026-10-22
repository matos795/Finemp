export interface DreLine {
    dreClassification: string
    dreOpenClassification: string
    ebitdaClassification: string
    total: number
}

export interface DreResult {
    lines: DreLine[]
    unclassifiedAccountCount: number
}

export interface DrePeriod {
    startDate: string
    endDate: string
}