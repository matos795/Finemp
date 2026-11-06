package com.finemp.administration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice(basePackages = "com.finemp.administration")
public class SaasAdministrationExceptionHandler {

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            IllegalArgumentException.class})
    ProblemDetail handleInvalidRequest(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetros administrativos inválidos.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityConflict() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O cadastro conflita com dados existentes.");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail handleAccessDenied(OrganizationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail handleNotFound(SaasResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
    }

    @ExceptionHandler({
            CompanyAssignmentConflictException.class,
            HierarchyDeletionConflictException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }
}
