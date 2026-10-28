package com.finemp.finance.transfer;

import com.finemp.administration.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=BankTransferCommandController.class)
public class BankTransferCommandExceptionHandler {
    @ExceptionHandler(com.finemp.finance.period.FinancialPeriodClosedException.class)
    ProblemDetail periodClosed() {return problem(HttpStatus.CONFLICT,"Um dos períodos financeiros afetados está fechado.","FINANCIAL_PERIOD_CLOSED");}
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Comando de transferência inválido.","INVALID_BANK_TRANSFER_COMMAND");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão para transferências nesta empresa.","BANK_TRANSFER_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Transferência financeira não encontrada.","BANK_TRANSFER_NOT_FOUND");}
    @ExceptionHandler(BankTransferCommandConflictException.class)
    ProblemDetail conflict(BankTransferCommandConflictException conflict) {return problem(HttpStatus.CONFLICT,"O comando conflita com o estado atual.",conflict.getReason().name());}
    @ExceptionHandler({OptimisticLockingFailureException.class,PessimisticLockingFailureException.class,DataIntegrityViolationException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"Dados vinculados ou alterados. Consulte o estado atual.","BANK_TRANSFER_CONFLICT");}
    @ExceptionHandler({DataAccessResourceFailureException.class,QueryTimeoutException.class})
    ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Comando de transferência indisponível. Tente novamente.","BANK_TRANSFER_COMMAND_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
