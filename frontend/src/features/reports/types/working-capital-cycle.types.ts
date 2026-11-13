export interface WorkingCapitalCycleReport {
    from: string
    to: string
    periodDays: number
    receivableTitles: number
    payableTitles: number
    receivablesDays: string | null
    payablesDays: string | null
    inventoryDays: string | null
    operatingCycleDays: string | null
    financialCycleDays: string | null
    dailyRevenue: string
    cycleWorkingCapital: string | null
}
