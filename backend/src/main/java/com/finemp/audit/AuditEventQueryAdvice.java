package com.finemp.audit;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuditEventQueryController.class)
public class AuditEventQueryAdvice {

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail handleAccessDenied(OrganizationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
    }
}
