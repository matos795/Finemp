package com.finemp.finance.reconciliation;

import com.finemp.administration.*;
import org.springframework.http.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=StatementPreviewController.class)
public class StatementPreviewExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_STATEMENT_PREVIEW");}
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"STATEMENT_PREVIEW_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class)
    ResponseEntity<?> missing(){return result(HttpStatus.NOT_FOUND,"BANK_ACCOUNT_NOT_FOUND");}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
