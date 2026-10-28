package com.finemp.finance.period;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={FinancialPeriodController.class,FinancialPeriodReadController.class})
public class FinancialPeriodExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de período financeiro inválidos.","INVALID_FINANCIAL_PERIOD");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão para períodos desta empresa.","FINANCIAL_PERIOD_ACCESS_DENIED");}
    @ExceptionHandler(FinancialPeriodConflictException.class)
    ProblemDetail conflict(FinancialPeriodConflictException conflict) {return problem(HttpStatus.CONFLICT,"O período foi alterado ou o comando conflita com o estado atual.",conflict.reason().name());}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"Período alterado. Consulte o estado atual.","FINANCIAL_PERIOD_CONFLICT");}
    @ExceptionHandler({DataAccessResourceFailureException.class,QueryTimeoutException.class})
    ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Períodos financeiros indisponíveis. Tente novamente.","FINANCIAL_PERIOD_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
