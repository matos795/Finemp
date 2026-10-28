package com.finemp.finance.entry;

import com.finemp.administration.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Fixed client messages only; never return source JSON, SQL or private financial text. */
@RestControllerAdvice(assignableTypes={FinancialEntryCommandController.class,EntrySettlementController.class})
public class FinancialEntryCommandExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Comando financeiro inválido.","INVALID_FINANCIAL_ENTRY_COMMAND");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão financeira para esta empresa.","FINANCIAL_ENTRY_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Lançamento financeiro não encontrado.","FINANCIAL_ENTRY_NOT_FOUND");}
    @ExceptionHandler(FinancialEntryCommandConflictException.class)
    ProblemDetail conflict(FinancialEntryCommandConflictException conflict) {return problem(HttpStatus.CONFLICT,"O comando conflita com o estado atual.",conflict.getReason().name());}
    @ExceptionHandler({OptimisticLockingFailureException.class,PessimisticLockingFailureException.class,DataIntegrityViolationException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"Dados financeiros vinculados ou alterados. Consulte o estado atual.","FINANCIAL_ENTRY_CONFLICT");}
    @ExceptionHandler({DataAccessResourceFailureException.class,QueryTimeoutException.class})
    ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Escrita financeira indisponível. Tente novamente.","FINANCIAL_ENTRY_COMMAND_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
