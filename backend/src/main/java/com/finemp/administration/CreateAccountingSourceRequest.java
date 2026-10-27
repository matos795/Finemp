package com.finemp.administration;

import com.finemp.accounting.source.AccountingSourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountingSourceRequest(
        @NotNull AccountingSourceType type,
        @Size(max = 255) String externalReference) {
}
