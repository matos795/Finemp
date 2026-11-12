package com.finemp.sales;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.finemp.sales")
public class SalesExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Parâmetros comerciais inválidos.",
                "INVALID_SALES_REQUEST");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail forbidden(OrganizationAccessDeniedException ignored) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Sem permissão comercial para esta empresa.",
                "CRM_ACCESS_DENIED");
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail notFound(SaasResourceNotFoundException ignored) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Registro comercial não encontrado.",
                "SALES_NOT_FOUND");
    }

    @ExceptionHandler(SalesConflictException.class)
    ProblemDetail conflict(SalesConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "O registro comercial conflita com o estado atual.",
                exception.getReason().name());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ignored) {
        return problem(
                HttpStatus.CONFLICT,
                "O cadastro comercial conflita com dados existentes.",
                "SALES_DATA_CONFLICT");
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    ProblemDetail concurrent(RuntimeException ignored) {
        return problem(
                HttpStatus.CONFLICT,
                "O registro comercial foi alterado por outra operação.",
                "SALES_CONFLICT");
    }

    private ProblemDetail problem(
            HttpStatus status,
            String detail,
            String code) {

        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
