package com.finemp.administration.firmaccess;

import com.finemp.access.AccountingFirmPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SetFirmPermissionsRequest(@NotNull Set<@NotNull AccountingFirmPermission> permissions) { }
