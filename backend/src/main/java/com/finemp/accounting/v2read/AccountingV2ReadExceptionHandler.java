package com.finemp.accounting.v2read;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountingV2ReadController.class)
class AccountingV2ReadExceptionHandler {

    @ExceptionHandler(AccountingSourceNotInCompanyException.class)
    ProblemDetail handleSourceNotInCompany(AccountingSourceNotInCompanyException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidAccountingV2ReadRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidAccountingV2ReadRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
