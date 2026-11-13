package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConsistencyDiagnosticsController.class)
public class ConsistencyDiagnosticsExceptionHandler {

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ResponseEntity<?> denied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("code", "CONSISTENCY_DIAGNOSTICS_ACCESS_DENIED"));
    }
}
