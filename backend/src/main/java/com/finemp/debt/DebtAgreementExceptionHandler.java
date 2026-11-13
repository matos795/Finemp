package com.finemp.debt;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = DebtAgreementController.class)
public class DebtAgreementExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Confira os dados da dívida.");
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

    @ExceptionHandler(DebtConflictException.class)
    ProblemDetail conflict(DebtConflictException exception) {
        var detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        detail.setProperty("code", "DEBT_STALE_VERSION");
        return detail;
    }
}
