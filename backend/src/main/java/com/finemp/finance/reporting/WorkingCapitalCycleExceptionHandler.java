package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = WorkingCapitalCycleController.class)
public class WorkingCapitalCycleExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Confira o período e o PME informados.");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied(OrganizationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
    }
}
