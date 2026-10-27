package com.finemp.connector;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateConnectorCredentialRequest(@NotNull UUID accountingSourceId, @NotBlank @Size(max = 80) String label) { }
