package com.finemp.finance.bank;

import com.finemp.administration.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(basePackages="com.finemp.finance.bank")
public class BankAccountExceptionHandler{
    @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored){return problem(HttpStatus.BAD_REQUEST,"Parâmetros de conta financeira inválidos.","INVALID_BANK_ACCOUNT");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied(){return problem(HttpStatus.FORBIDDEN,"Sem permissão financeira para esta empresa.","BANK_ACCOUNT_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing(){return problem(HttpStatus.NOT_FOUND,"Conta financeira não encontrada.","BANK_ACCOUNT_NOT_FOUND");}
    @ExceptionHandler(BankAccountConflictException.class)
    ProblemDetail conflict(BankAccountConflictException conflict){return problem(HttpStatus.CONFLICT,"A operação conflita com o estado atual da conta.",conflict.reason().name());}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    ProblemDetail concurrent(){return problem(HttpStatus.CONFLICT,"A conta está vinculada ou foi alterada. Consulte o estado atual.","BANK_ACCOUNT_CONFLICT");}
    private ProblemDetail problem(HttpStatus status,String message,String code){var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;}
}
