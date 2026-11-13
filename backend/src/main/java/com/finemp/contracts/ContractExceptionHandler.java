package com.finemp.contracts;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.finemp.contracts")
public class ContractExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Confira os dados do contrato.");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied(OrganizationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
    }

    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail notFound(SaasResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
    }

    @ExceptionHandler(ContractConflictException.class)
    ProblemDetail conflict(ContractConflictException exception) {
        var detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        detail.setProperty(
                "code",
                exception.reason() == ContractConflictException.Reason.REFERENCED_PARTNER_CHANGE
                        ? "CONTRACT_REFERENCED_PARTNER_CHANGE"
                        : "CONTRACT_STALE_VERSION");
        return detail;
    }
}
