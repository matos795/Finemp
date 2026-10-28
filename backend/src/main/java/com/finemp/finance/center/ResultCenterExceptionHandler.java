package com.finemp.finance.center;

import com.finemp.administration.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(basePackages="com.finemp.finance.center")
public class ResultCenterExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de centro de resultado inválidos.","INVALID_RESULT_CENTER");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão de centros para esta empresa.","RESULT_CENTER_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Centro de resultado não encontrado.","RESULT_CENTER_NOT_FOUND");}
    @ExceptionHandler(ResultCenterConflictException.class)
    ProblemDetail conflict(ResultCenterConflictException conflict) {return problem(HttpStatus.CONFLICT,"A operação conflita com o estado atual dos centros.",conflict.reason().name());}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"O centro está vinculado, duplicado ou foi alterado. Consulte o estado atual.","RESULT_CENTER_CONFLICT");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {
        var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;
    }
}
