package com.finemp.finance.category;

import jakarta.validation.constraints.*;

public record FinancialCategoryGroupRequest(@NotNull FinancialDirection direction,@NotBlank @Size(max=255) String name) { }
