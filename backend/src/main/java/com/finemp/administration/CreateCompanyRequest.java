package com.finemp.administration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @NotBlank @Size(max = 255) String legalName,
        @NotBlank @Pattern(regexp = "\\d{14}") String cnpj) {
}
