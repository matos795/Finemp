package com.finemp.finance.category;

import com.finemp.administration.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(basePackages="com.finemp.finance.category")
public class FinancialCategoryExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,ConstraintViolationException.class,HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    ProblemDetail invalid(Exception ignored) {return problem(HttpStatus.BAD_REQUEST,"Parâmetros de categoria financeira inválidos.","INVALID_FINANCIAL_CATEGORY");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ProblemDetail denied() {return problem(HttpStatus.FORBIDDEN,"Sem permissão de categorias para esta empresa.","FINANCIAL_CATEGORY_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ProblemDetail missing() {return problem(HttpStatus.NOT_FOUND,"Categoria financeira não encontrada.","FINANCIAL_CATEGORY_NOT_FOUND");}
    @ExceptionHandler(FinancialCategoryConflictException.class)
    ProblemDetail conflict(FinancialCategoryConflictException conflict) {return problem(HttpStatus.CONFLICT,"A operação conflita com o estado atual das categorias.",conflict.reason().name());}
    @ExceptionHandler({DataIntegrityViolationException.class,OptimisticLockingFailureException.class,PessimisticLockingFailureException.class})
    ProblemDetail concurrent() {return problem(HttpStatus.CONFLICT,"A categoria está vinculada ou foi alterada. Consulte o estado atual.","FINANCIAL_CATEGORY_CONFLICT");}
    private ProblemDetail problem(HttpStatus status,String message,String code) {
        var result=ProblemDetail.forStatusAndDetail(status,message);result.setProperty("code",code);return result;
    }
}
