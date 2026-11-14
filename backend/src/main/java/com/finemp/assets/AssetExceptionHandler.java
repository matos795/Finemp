package com.finemp.assets;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AssetController.class)
public class AssetExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ProblemDetail invalid(Exception ignored) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Parâmetros patrimoniais inválidos.",
                "INVALID_ASSET");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {
        return problem(
                HttpStatus.FORBIDDEN,
                "Sem permissão de bens para esta empresa.",
                "ASSET_ACCESS_DENIED");
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {
        return problem(
                HttpStatus.NOT_FOUND,
                "Bem patrimonial ou vínculo não encontrado.",
                "ASSET_NOT_FOUND");
    }

    @ExceptionHandler(AssetConflictException.class)
    ProblemDetail conflict(AssetConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "A operação conflita com o estado atual do bem.",
                switch (exception.reason()) {
                    case STALE_VERSION -> "ASSET_STALE_VERSION";
                    case DUPLICATE_TAG -> "ASSET_DUPLICATE_TAG";
                });
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    ProblemDetail concurrent() {
        return problem(
                HttpStatus.CONFLICT,
                "O bem está vinculado, duplicado ou foi alterado. Consulte o estado atual.",
                "ASSET_CONFLICT");
    }

    private ProblemDetail problem(
            HttpStatus status,
            String message,
            String code) {
        var result = ProblemDetail.forStatusAndDetail(status, message);
        result.setProperty("code", code);
        return result;
    }
}
