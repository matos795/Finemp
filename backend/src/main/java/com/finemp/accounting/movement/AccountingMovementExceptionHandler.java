package com.finemp.accounting.movement;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountingMovementExceptionHandler {

    @ExceptionHandler(InvalidAccountingPeriodException.class)
    ProblemDetail handleInvalidAccountingPeriod(
            InvalidAccountingPeriodException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
    }
}