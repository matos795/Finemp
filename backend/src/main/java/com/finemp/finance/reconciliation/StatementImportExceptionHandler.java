package com.finemp.finance.reconciliation;

import com.finemp.administration.*;
import com.finemp.finance.period.FinancialPeriodClosedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=StatementImportController.class)
public class StatementImportExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_STATEMENT_IMPORT");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"STATEMENT_IMPORT_ACCESS_DENIED");}
    @ExceptionHandler(SaasResourceNotFoundException.class) ResponseEntity<?> missing(){return result(HttpStatus.NOT_FOUND,"BANK_ACCOUNT_NOT_FOUND");}
    @ExceptionHandler(FinancialPeriodClosedException.class) ResponseEntity<?> closed(){return result(HttpStatus.CONFLICT,"FINANCIAL_PERIOD_CLOSED");}
    @ExceptionHandler(StatementImportConflictException.class) ResponseEntity<?> conflict(StatementImportConflictException error){return result(HttpStatus.CONFLICT,error.getReason().name());}
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> constraint(){return result(HttpStatus.CONFLICT,"STATEMENT_IMPORT_CONFLICT");}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
