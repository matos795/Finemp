package com.finemp.finance.bank;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BankAccountRequest(@NotBlank @Size(max=255) String name,@NotNull BankAccountType type,
        @NotNull BigDecimal openingBalance,@NotNull LocalDate openingDate,@NotNull Boolean active,@PositiveOrZero Long expectedVersion){ }
