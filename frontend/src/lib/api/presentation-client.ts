import { apiRequest } from "./api-client.ts"

// Decimal text is transported unchanged. Financial calculations belong to the API.
export type DecimalText = string
export type AgingScope = "ALL" | "COMPETENCE"
export type DreLine = "REC" | "DED" | "CUS" | "DCOM" | "DADM" | "DPES" | "DTRI" | "OUT" | "DEP" | "RFIN" | "DFIN" | "IR" | "NAO"
export interface CashAmounts {
  realIncome: DecimalText
  realExpense: DecimalText
  openIncome: DecimalText
  openExpense: DecimalText
}
export interface CashPeriod extends CashAmounts {
  period: number
  firstMonth: number
  lastMonth: number
}
export interface CashYear {
  year: number
  months: (CashAmounts & { month: number })[]
  activityMonths: (CashAmounts & { month: number; activity: "OPERATING" | "INVESTING" | "FINANCING" })[]
  quarters: CashPeriod[]
  halfYears: CashPeriod[]
  totalRealIncome: DecimalText
  totalRealExpense: DecimalText
  totalOpenIncome: DecimalText
  totalOpenExpense: DecimalText
}
export interface AgingReport {
  asOf: string
  direction: "INCOME" | "EXPENSE"
  scope: AgingScope
  totalCount: number
  totalAmount: DecimalText
  overdueCount: number
  overdueAmount: DecimalText
  dueWithin30Count: number
  dueWithin30Amount: DecimalText
  bands: { band: string; count: number; amount: DecimalText }[]
}
export interface PartialCoverage {
  partial: true
  coverage: "FINANCIAL_ENTRIES"[]
}
export interface OperationalDre extends PartialCoverage {
  companyId: string
  month: string
  lines: { line: Exclude<DreLine, "NAO">; competenceAmount: DecimalText; settlementDifference: DecimalText; amount: DecimalText }[]
  subtotals: {
    netRevenue: DecimalText
    grossProfit: DecimalText
    ebitda: DecimalText
    ebit: DecimalText
    profitBeforeTax: DecimalText
    netResult: DecimalText
  }
}
export type Ratio = {
  numerator: DecimalText
  denominator: DecimalText
} & ({ availability: "AVAILABLE"; percent: DecimalText } | { availability: "UNAVAILABLE_BASE"; percent: null })
export interface PresentationIndicators {
  percentUnit: "PERCENT"
  percentDecimalPlaces: 1
  cash: { realizedIncome: DecimalText; realizedExpense: DecimalText; generation: DecimalText; margin: Ratio }
  receivables: { overdueRate: Ratio }
  dre?: PartialCoverage & { grossMargin: Ratio; ebitdaMargin: Ratio; netMargin: Ratio }
}
export interface PresentationSnapshot {
  schemaVersion: 1
  partial: true
  companyId: string
  month: string
  asOf: string
  scope: AgingScope
  cashMonth: { month: string; days: (CashAmounts & { date: string })[] }
  cashYear: CashYear
  payables: AgingReport
  receivables: AgingReport
  operationalDre?: OperationalDre
  indicators: PresentationIndicators
}
export interface DreEffect {
  entryId: string
  description: string
  categoryId: string
  competence: string
  paymentDate: string | null
  line: Exclude<DreLine, "NAO">
  source: "COMPETENCE" | "SETTLEMENT_DIFFERENCE"
  amount: DecimalText
}
export interface DreEffectsPage extends PartialCoverage {
  companyId: string
  month: string
  items: DreEffect[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
export interface SnapshotQuery { month: string; asOf: string; scope: AgingScope }
export interface EffectsQuery { month: string; line?: DreLine; page: number; size: number }

function reportPath(companyId: string, resource: string, query: URLSearchParams): string {
  return `/api/companies/${encodeURIComponent(companyId)}/financial-reports/${resource}?${query}`
}

/** No date defaults, extra requests, derived totals or fallback data. */
export function getPresentationSnapshot(companyId: string, query: SnapshotQuery, signal?: AbortSignal): Promise<PresentationSnapshot> {
  const params = new URLSearchParams({ month: query.month, asOf: query.asOf, scope: query.scope })
  return apiRequest<PresentationSnapshot>(reportPath(companyId, "presentation-snapshot", params), { signal, cache: "no-store" })
}

/** Monthly operational DRE (#41). Not the accounting Questor DRE; no month default. */
export function getOperationalDre(companyId: string, month: string, signal?: AbortSignal): Promise<OperationalDre> {
  const params = new URLSearchParams({ month })
  return apiRequest<OperationalDre>(reportPath(companyId, "operational-dre", params), { signal, cache: "no-store" })
}

/** A later detail request does not share the earlier snapshot's database transaction. */
export function getOperationalDreEffects(companyId: string, query: EffectsQuery, signal?: AbortSignal): Promise<DreEffectsPage> {
  const params = new URLSearchParams({ month: query.month, page: String(query.page), size: String(query.size) })
  if (query.line !== undefined) params.set("line", query.line)
  return apiRequest<DreEffectsPage>(reportPath(companyId, "operational-dre/effects", params), { signal, cache: "no-store" })
}
