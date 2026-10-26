package com.finemp.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception) {

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.PAYLOAD_TOO_LARGE);

        problem.setTitle(
                "Arquivo muito grande");

        problem.setDetail(
                "O arquivo excede o limite permitido de 20 MB.");

        return problem;
    }
}