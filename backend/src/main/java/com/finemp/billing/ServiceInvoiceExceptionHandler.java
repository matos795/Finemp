package com.finemp.billing;

import com.finemp.administration.OrganizationAccessDeniedException;
import com.finemp.administration.SaasResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ServiceInvoiceController.class)
public class ServiceInvoiceExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ProblemDetail invalid() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Confira os dados da nota fiscal.");
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

    @ExceptionHandler(ServiceInvoiceConflictException.class)
    ProblemDetail conflict(ServiceInvoiceConflictException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }
}
