package com.finemp.security;

import com.finemp.access.PlatformPermission;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String name,
        String email,
        List<PlatformPermission> platformPermissions,
        List<CurrentUserCompanyAccessResponse> companies) {
}
