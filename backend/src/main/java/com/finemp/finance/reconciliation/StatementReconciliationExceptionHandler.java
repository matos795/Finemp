package com.finemp.finance.reconciliation;

import com.finemp.administration.*;
import com.finemp.finance.entry.FinancialEntryCommandConflictException;
import com.finemp.finance.period.FinancialPeriodClosedException;
import org.springframework.http.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=StatementReconciliationController.class)
public class StatementReconciliationExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_STATEMENT_RECONCILIATION");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"STATEMENT_RECONCILIATION_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class) ResponseEntity<?> missing(){return result(HttpStatus.NOT_FOUND,"STATEMENT_RECONCILIATION_TARGET_NOT_FOUND");}
    @ExceptionHandler(FinancialPeriodClosedException.class) ResponseEntity<?> closed(){return result(HttpStatus.CONFLICT,"FINANCIAL_PERIOD_CLOSED");}
    @ExceptionHandler(StatementActionConflictException.class) ResponseEntity<?> conflict(StatementActionConflictException error){return result(HttpStatus.CONFLICT,error.getReason().name());}
    @ExceptionHandler(FinancialEntryCommandConflictException.class) ResponseEntity<?> entryConflict(FinancialEntryCommandConflictException error){return result(HttpStatus.CONFLICT,error.getReason().name());}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
