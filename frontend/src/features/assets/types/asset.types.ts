import type { PageResponse } from "../../../lib/api/pagination.types.ts"

export type AssetCategory =
    | "IT"
    | "VEHICLE"
    | "MACHINERY"
    | "FURNITURE"
    | "INSTALLATION"
    | "BUILDING"
    | "LAND"
    | "INTANGIBLE"
    | "OTHER"

export type AssetStatus =
    | "IN_USE"
    | "MAINTENANCE"
    | "IDLE"
    | "DISPOSED"

export interface Asset {
    id: string
    assetTag: string | null
    description: string
    category: AssetCategory
    brandModel: string | null
    serialNumber: string | null
    acquisitionDate: string
    acquisitionValue: string
    quantity: number
    businessPartnerId: string | null
    businessPartnerName: string | null
    documentReference: string | null
    resultCenterId: string | null
    resultCenterCode: string | null
    resultCenterName: string | null
    location: string | null
    responsible: string | null
    usefulLifeMonths: number
    residualPercent: string
    depreciable: boolean
    depreciationStartDate: string | null
    status: AssetStatus
    warrantyUntil: string | null
    notes: string | null
    monthlyDepreciation: string
    accumulatedDepreciation: string
    bookValue: string
    version: number
}

export interface AssetSummary {
    activeCount: number
    acquisitionValue: string
    bookValue: string
    monthlyDepreciation: string
}

export type AssetPage = PageResponse<Asset>

export interface AssetFilter {
    search: string
    status: AssetStatus | ""
    category: AssetCategory | ""
}

export interface AssetValues {
    assetTag: string | null
    description: string
    category: AssetCategory
    brandModel: string | null
    serialNumber: string | null
    acquisitionDate: string
    acquisitionValue: string
    quantity: number
    businessPartnerId: string | null
    documentReference: string | null
    resultCenterId: string | null
    location: string | null
    responsible: string | null
    usefulLifeMonths: number
    residualPercent: string
    depreciable: boolean
    depreciationStartDate: string | null
    status: Exclude<AssetStatus, "DISPOSED">
    warrantyUntil: string | null
    notes: string | null
}

export const assetCategoryOptions: Array<{
    value: AssetCategory
    label: string
    usefulLifeMonths: number
    depreciable: boolean
}> = [
    { value: "IT", label: "Computadores e informática", usefulLifeMonths: 60, depreciable: true },
    { value: "VEHICLE", label: "Veículos", usefulLifeMonths: 60, depreciable: true },
    { value: "MACHINERY", label: "Máquinas e equipamentos", usefulLifeMonths: 120, depreciable: true },
    { value: "FURNITURE", label: "Móveis e utensílios", usefulLifeMonths: 120, depreciable: true },
    { value: "INSTALLATION", label: "Instalações", usefulLifeMonths: 120, depreciable: true },
    { value: "BUILDING", label: "Edificações (imóveis)", usefulLifeMonths: 300, depreciable: true },
    { value: "LAND", label: "Terrenos", usefulLifeMonths: 0, depreciable: false },
    { value: "INTANGIBLE", label: "Softwares e marcas (intangível)", usefulLifeMonths: 60, depreciable: true },
    { value: "OTHER", label: "Outros bens", usefulLifeMonths: 120, depreciable: true },
]

export const assetStatusLabel: Record<AssetStatus, string> = {
    IN_USE: "Em uso",
    MAINTENANCE: "Em manutenção",
    IDLE: "Parado",
    DISPOSED: "Baixado",
}
