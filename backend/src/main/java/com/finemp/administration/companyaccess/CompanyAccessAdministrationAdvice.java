package com.finemp.administration.companyaccess;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanyAccessAdministrationController.class)
public class CompanyAccessAdministrationAdvice {
    @ExceptionHandler(CompanyAccessConflictException.class)
    ProblemDetail conflict(CompanyAccessConflictException conflict) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, conflict.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalidPayload() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Payload administrativo inválido.");
    }
}
