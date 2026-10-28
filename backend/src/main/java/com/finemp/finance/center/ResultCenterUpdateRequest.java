package com.finemp.finance.center;

import jakarta.validation.constraints.*;

public record ResultCenterUpdateRequest(@NotBlank @Size(max=80) String name,@NotNull Boolean active,@NotNull @PositiveOrZero Long expectedVersion) { }
