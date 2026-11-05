import type {
    AgingReport,
    AgingScope,
    DecimalText,
} from "../../../lib/api/presentation-client"
import type { PageResponse } from "../../../lib/api/pagination.types"

export type { AgingScope }

export type AgingDirection = AgingReport["direction"]

export type AgingBandCode =
    | "DUE_0_30"
    | "DUE_31_60"
    | "DUE_61_90"
    | "DUE_OVER_90"
    | "OVERDUE_1_30"
    | "OVERDUE_31_60"
    | "OVERDUE_61_90"
    | "OVERDUE_OVER_90"

/**
 * Resposta de getOpenTitlesAging. Reaproveita o tipo do cliente da
 * Apresentação de Resultados (mesmo contrato), sem redefinir campos.
 */
export type OpenTitlesAging = AgingReport

/** Filtros comuns a aging, lista e subtotais por parceiro. */
export interface OpenTitlesFilter {
    direction: AgingDirection
    asOf: string
    scope: AgingScope
    band?: AgingBandCode
}

/** Item de listOpenTitlesAsOf. Dias de atraso e faixa vêm do servidor. */
export interface OpenTitle {
    id: string
    description: string
    direction: AgingDirection
    nominalAmount: DecimalText
    competence: string
    dueDate: string
    paymentDate: string | null
    daysLate: number
    documentNumber: string
    documentDate: string
    categoryId: string
    band: AgingBandCode
    bankAccountId: string
    partnerId: string | null
    resultCenterId: string | null
    version: number
}

/** Item de listOpenTitlePartnerTotals: subtotal calculado no servidor. */
export interface OpenTitlePartnerTotal {
    partnerId: string | null
    partnerName: string | null
    titleCount: number
    nominalAmount: DecimalText
}

export type OpenTitlesPage = PageResponse<OpenTitle>

export type OpenTitlePartnerTotalsPage = PageResponse<OpenTitlePartnerTotal>
