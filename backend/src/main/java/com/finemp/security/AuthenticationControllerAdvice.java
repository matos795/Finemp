package com.finemp.security;

import com.finemp.identity.TooManyIdentityAttemptsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthenticationControllerAdvice {

    @ExceptionHandler(TooManyIdentityAttemptsException.class)
    public ResponseEntity<Void> handleAttemptLimit() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Void> handleAuthenticationFailure(
            AuthenticationException exception) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .build();
    }
}
