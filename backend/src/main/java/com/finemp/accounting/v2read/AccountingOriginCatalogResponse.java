package com.finemp.accounting.v2read;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo técnico de origem: código cru e descrição técnica da origem, sem
 * significado de negócio. AVAILABLE somente com cobertura snapshot publicada.
 */
public record AccountingOriginCatalogResponse(
        UUID accountingSourceId,
        Availability availability,
        @JsonInclude(JsonInclude.Include.ALWAYS) Instant publishedAt,
        List<Item> items) {

    public enum Availability { AVAILABLE, NOT_PUBLISHED }

    public record Item(String originCode, String description) {}

    public AccountingOriginCatalogResponse {
        items = List.copyOf(items);
    }
}
