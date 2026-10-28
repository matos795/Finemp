package com.finemp.finance.category;

import jakarta.validation.constraints.*;

public record FinancialSubcategoryRequest(@NotBlank @Size(max=255) String name) { }
