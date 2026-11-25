package com.finemp.people;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {JobPositionController.class, EmployeeController.class})
public class PeopleExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    ProblemDetail invalid(Exception ignored) {
        return problem(HttpStatus.BAD_REQUEST, "Parâmetros de pessoas inválidos.", "INVALID_PEOPLE");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {
        return problem(HttpStatus.FORBIDDEN, "Sem permissão de gestão de pessoas para esta empresa.",
                "PEOPLE_ACCESS_DENIED");
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {
        return problem(HttpStatus.NOT_FOUND, "Cargo, funcionário ou vínculo não encontrado.", "PEOPLE_NOT_FOUND");
    }

    @ExceptionHandler(PeopleConflictException.class)
    ProblemDetail conflict(PeopleConflictException exception) {
        return problem(HttpStatus.CONFLICT, "A operação conflita com o estado atual do cadastro.",
                switch (exception.reason()) {
                    case STALE_VERSION -> "PEOPLE_STALE_VERSION";
                    case DUPLICATE_POSITION -> "PEOPLE_DUPLICATE_POSITION";
                });
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    ProblemDetail concurrent() {
        return problem(HttpStatus.CONFLICT, "O cadastro foi alterado ou está duplicado. Consulte o estado atual.",
                "PEOPLE_CONFLICT");
    }

    private static ProblemDetail problem(HttpStatus status, String message, String code) {
        var result = ProblemDetail.forStatusAndDetail(status, message);
        result.setProperty("code", code);
        return result;
    }
}
