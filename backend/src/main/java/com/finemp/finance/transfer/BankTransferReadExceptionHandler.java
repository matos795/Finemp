package com.finemp.finance.transfer;

import com.finemp.administration.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=BankTransferReadController.class)
public class BankTransferReadExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de consulta inválidos.","INVALID_BANK_TRANSFER_QUERY");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão para consultar transferências nesta empresa.","BANK_TRANSFER_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Transferência financeira não encontrada.","BANK_TRANSFER_NOT_FOUND");}
    @ExceptionHandler({QueryTimeoutException.class,DataAccessResourceFailureException.class})
    ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Consulta de transferências indisponível. Tente novamente.","BANK_TRANSFER_QUERY_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
