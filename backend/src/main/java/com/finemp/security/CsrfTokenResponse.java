package com.finemp.security;

public record CsrfTokenResponse(
        String headerName,
        String token) {
}