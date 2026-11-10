import type { FinancialDirection } from "./financial-entry.types.ts"

/** Item de `listFinancialCategories` usado no formulário e no filtro. */
export interface FinancialCategoryOption {
    id: string
    parentId: string | null
    kind: "GROUP" | "SUBCATEGORY"
    direction: FinancialDirection
    code: string
    name: string
    effective: {
        costBehavior: "FIXED" | "VARIABLE" | null
    }
}

export type BusinessPartnerRole = "CUSTOMER" | "SUPPLIER" | "BOTH"

/** Item de `listBusinessPartners`: só os campos que o seletor exibe. */
export interface BusinessPartnerOption {
    id: string
    profile: {
        name: string
        tradeName: string | null
        role: BusinessPartnerRole
        active: boolean
    }
}

/** Item de `listResultCenters`. Só ANALYTICAL recebe lançamento. */
export interface ResultCenterOption {
    id: string
    parentId: string | null
    code: string
    name: string
    active: boolean
    type: "GROUP" | "ANALYTICAL"
}

export interface ResultCenterSettings {
    requireResultCenter: boolean
}
