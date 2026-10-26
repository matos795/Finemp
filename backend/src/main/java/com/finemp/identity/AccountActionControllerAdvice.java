package com.finemp.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.finemp.identity")
public class AccountActionControllerAdvice {
    @ExceptionHandler({InvalidAccountActionException.class, InvalidAccountPasswordException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Void> invalidAction() {
        return ResponseEntity.badRequest().build();
    }
}
