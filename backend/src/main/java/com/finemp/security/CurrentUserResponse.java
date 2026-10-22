package com.finemp.security;

import java.util.List;
import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String name,
        String email,
        List<CurrentUserCompanyAccessResponse> companies) {
}