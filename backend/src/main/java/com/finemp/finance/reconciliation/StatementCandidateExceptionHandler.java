package com.finemp.finance.reconciliation;

import com.finemp.administration.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=StatementCandidateController.class)
public class StatementCandidateExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_STATEMENT_CANDIDATES");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"STATEMENT_CANDIDATES_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class) ResponseEntity<?> missing(){return result(HttpStatus.NOT_FOUND,"STATEMENT_ITEM_NOT_FOUND");}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
