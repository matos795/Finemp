package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=BankAccountBalanceReadController.class)
public class BankAccountBalanceReadExceptionHandler {
    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ResponseEntity<?> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("code","ACCOUNT_BALANCE_ACCESS_DENIED"));
    }
}
