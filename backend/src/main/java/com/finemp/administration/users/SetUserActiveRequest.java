package com.finemp.administration.users;

import jakarta.validation.constraints.NotNull;

public record SetUserActiveRequest(@NotNull Boolean active) { }
