package com.finemp.finance.entry;

import com.finemp.administration.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.validation.BindException;

@RestControllerAdvice(basePackageClasses=FinancialEntryReadController.class)
public class FinancialEntryReadExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,BindException.class,HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de consulta financeira inválidos.","INVALID_FINANCIAL_ENTRY_QUERY");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão financeira para esta empresa.","FINANCIAL_ENTRY_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Recurso financeiro não encontrado.","FINANCIAL_ENTRY_NOT_FOUND");}
    @ExceptionHandler({QueryTimeoutException.class,DataAccessResourceFailureException.class})
    ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Consulta financeira indisponível. Tente novamente.","FINANCIAL_ENTRY_QUERY_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {
        var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;
    }
}
