package com.finemp.finance.partner;

import com.finemp.administration.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

/** Fixed messages: never disclose personal fields, unique-key values or SQL diagnostics. */
@RestControllerAdvice(basePackages="com.finemp.finance.partner")
public class BusinessPartnerExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de parceiro inválidos.","INVALID_BUSINESS_PARTNER");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão de parceiros para esta empresa.","BUSINESS_PARTNER_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Parceiro comercial não encontrado.","BUSINESS_PARTNER_NOT_FOUND");}
    @ExceptionHandler(BusinessPartnerConflictException.class)
    ProblemDetail conflict(BusinessPartnerConflictException conflict) {return problem(HttpStatus.CONFLICT,"A operação conflita com o estado atual do parceiro.",conflict.reason().name());}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"O cadastro está vinculado, duplicado ou foi alterado. Consulte o estado atual.","BUSINESS_PARTNER_CONFLICT");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {
        var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;
    }
}
