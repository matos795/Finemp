package com.finemp.company;

import com.finemp.access.CompanyAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CompanyProfileController.class)
public class CompanyProfileAdvice {

    @ExceptionHandler(CompanyAccessDeniedException.class)
    ProblemDetail handleAccessDenied(CompanyAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
    }
}
