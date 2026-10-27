package com.finemp.administration.users;

import com.finemp.access.PlatformPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record SetPlatformPermissionsRequest(@NotNull Set<@NotNull PlatformPermission> permissions) { }
