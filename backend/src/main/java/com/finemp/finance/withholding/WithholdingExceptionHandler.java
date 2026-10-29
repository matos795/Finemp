package com.finemp.finance.withholding;

import com.finemp.administration.*;
import com.finemp.finance.period.FinancialPeriodClosedException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=WithholdingController.class)
public class WithholdingExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de retenção inválidos.","INVALID_FINANCIAL_WITHHOLDING");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão para retenções desta empresa.","WITHHOLDING_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class) ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Lançamento ou retenção não encontrado.","WITHHOLDING_NOT_FOUND");}
    @ExceptionHandler(WithholdingConflictException.class) ProblemDetail conflict(WithholdingConflictException conflict) {return problem(HttpStatus.CONFLICT,"O comando conflita com o estado atual.",conflict.reason().name());}
    @ExceptionHandler(FinancialPeriodClosedException.class) ProblemDetail closed() {return problem(HttpStatus.CONFLICT,"A competência do lançamento está fechada.","FINANCIAL_PERIOD_CLOSED");}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class}) ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"Dados vinculados ou alterados. Consulte o estado atual.","WITHHOLDING_CONFLICT");}
    @ExceptionHandler({DataAccessResourceFailureException.class,QueryTimeoutException.class}) ProblemDetail unavailable() {return problem(HttpStatus.SERVICE_UNAVAILABLE,"Retenções indisponíveis. Tente novamente.","WITHHOLDING_UNAVAILABLE");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
