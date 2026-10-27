package com.finemp.accounting.sync.control;

import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.administration.OrganizationAccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

@RestControllerAdvice(assignableTypes = SyncControlController.class)
public class SyncControlExceptionHandler {
    @ExceptionHandler(SyncControlConflictException.class)
    ProblemDetail conflict(SyncControlConflictException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", exception.getReason().name()); return problem;
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Solicitação de sincronização inválida.");
    }
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Sincronização não encontrada no escopo autorizado.");
    }
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso à sincronização não autorizado.");
    }
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ProblemDetail concurrentConflict() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Conflito concorrente; consulte a execução ou repita com a mesma chave idempotente.");
        problem.setProperty("code", "CONCURRENT_CONFLICT");
        return problem;
    }
}

