package com.finemp.security;

import com.finemp.identity.TooManyIdentityAttemptsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(basePackages = {"com.finemp.security", "com.finemp.identity"})
public class AuthenticationControllerAdvice {

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Void> handleInvalidCredentialsPayload() {
        return ResponseEntity.badRequest().build();
    }

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
