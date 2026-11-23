package com.finemp.dre;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros explícitos da DRE em vez de 500 (#449). Não decide a semântica de valores negativos. */
@RestControllerAdvice(assignableTypes = DreController.class)
public class DreExceptionHandler {

    @ExceptionHandler(InvalidDrePeriodException.class)
    ProblemDetail invalidPeriod() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Período inválido.");
    }

    @ExceptionHandler(DreUnsupportedMovementException.class)
    ProblemDetail unsupported() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "A base contém movimento com valor negativo; a regra da DRE para esse caso ainda não foi decidida.");
        problem.setProperty("code", "DRE_UNSUPPORTED_NEGATIVE_AMOUNT");
        return problem;
    }
}
