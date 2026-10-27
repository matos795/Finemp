package com.finemp.administration.users;

import com.finemp.access.PlatformPermission;
import java.util.Set;

public record UserAdministrationDetail(UserAdministrationSummary user, Set<PlatformPermission> platformPermissions) {
    public UserAdministrationDetail { platformPermissions = Set.copyOf(platformPermissions); }
}
