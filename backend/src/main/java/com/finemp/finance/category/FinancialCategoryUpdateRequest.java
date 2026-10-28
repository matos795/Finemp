package com.finemp.finance.category;

import jakarta.validation.constraints.*;

public record FinancialCategoryUpdateRequest(@NotBlank @Size(max=255) String name,CashActivity cashActivity,OperationalDreLine dreLine,
        CostBehavior costBehavior,@NotNull @PositiveOrZero Long expectedVersion) { }
