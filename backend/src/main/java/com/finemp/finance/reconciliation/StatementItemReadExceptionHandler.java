package com.finemp.finance.reconciliation;

import com.finemp.administration.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=StatementItemReadController.class)
public class StatementItemReadExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_STATEMENT_QUERY");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"STATEMENT_READ_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class) ResponseEntity<?> missing(){return result(HttpStatus.NOT_FOUND,"BANK_ACCOUNT_NOT_FOUND");}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
