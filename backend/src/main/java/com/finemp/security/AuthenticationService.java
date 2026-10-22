package com.finemp.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
            AuthenticationManager authenticationManager) {

        this.authenticationManager = authenticationManager;
    }

    public Authentication authenticate(
            String email,
            String password) {

        Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(
                email,
                password);

        return authenticationManager.authenticate(
                authenticationRequest);
    }
}