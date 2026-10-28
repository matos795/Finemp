package com.finemp.finance.center;

import jakarta.validation.constraints.*;

public record ResultCenterCreateRequest(@NotBlank @Size(max=100) String code,@NotBlank @Size(max=80) String name) { }
