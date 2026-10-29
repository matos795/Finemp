/**
 * Página devolvida pela API FINEMP (`PageMetadata` no OpenAPI).
 * `page` começa em 0. Totais são calculados pelo servidor.
 */
export interface PageResponse<T> {
    items: T[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export interface PageRequest {
    page: number
    size: number
}
