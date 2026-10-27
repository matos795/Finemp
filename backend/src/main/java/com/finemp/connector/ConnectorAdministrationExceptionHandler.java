package com.finemp.connector;

import com.finemp.administration.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = ConnectorCredentialController.class)
public class ConnectorAdministrationExceptionHandler {
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ProblemDetail invalid() { return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetros do Connector inválidos."); }
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    public ProblemDetail denied() { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso ao Connector não autorizado."); }
    @ExceptionHandler(SaasResourceNotFoundException.class)
    public ProblemDetail missing() { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso do Connector não encontrado."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict() { return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "O cadastro conflita com o estado atual."); }
}
