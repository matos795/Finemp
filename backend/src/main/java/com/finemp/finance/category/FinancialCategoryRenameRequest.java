package com.finemp.finance.category;

import jakarta.validation.constraints.*;

/**
 * Renomear grupo ou subcategoria. Só nome e versão: atividade de caixa,
 * linha da DRE e comportamento de custo não fazem parte deste comando
 * (campos extras no corpo são ignorados e nunca lidos).
 */
public record FinancialCategoryRenameRequest(@NotBlank @Size(max=255) String name,@NotNull @PositiveOrZero Long expectedVersion) { }
