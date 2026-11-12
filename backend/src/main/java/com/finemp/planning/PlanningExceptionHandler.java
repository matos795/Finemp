package com.finemp.planning;

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

@RestControllerAdvice(basePackages = "com.finemp.planning")
public class PlanningExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Parâmetros de planejamento inválidos.",
                "INVALID_PLANNING_REQUEST");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail forbidden(OrganizationAccessDeniedException ignored) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Sem permissão de planejamento para esta empresa.",
                "PLANNING_ACCESS_DENIED");
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail notFound(SaasResourceNotFoundException ignored) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Registro de planejamento não encontrado.",
                "PLANNING_NOT_FOUND");
    }

    @ExceptionHandler(PlanningConflictException.class)
    ProblemDetail conflict(PlanningConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "O registro foi alterado por outra operação. Atualize os dados e tente novamente.",
                exception.getReason().name());
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    ProblemDetail concurrent(RuntimeException ignored) {
        return problem(
                HttpStatus.CONFLICT,
                "O planejamento foi alterado ou conflita com o estado atual. Consulte novamente antes de continuar.",
                "PLANNING_CONFLICT");
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
