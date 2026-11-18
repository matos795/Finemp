package com.finemp.administration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountingSourceExternalReferenceRequest(
        @NotBlank
        @Size(max = 255)
        String externalReference) {
}
