package com.finemp.finance.center;

import jakarta.validation.constraints.*;

public record ResultCenterSettingsRequest(@NotNull Boolean requireResultCenter,@PositiveOrZero Long expectedVersion) { }
