package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = WorkingCapitalCycleController.class)
public class WorkingCapitalCycleExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> invalid() {
        return result(HttpStatus.BAD_REQUEST, "INVALID_WORKING_CAPITAL_CYCLE");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ResponseEntity<?> denied() {
        return result(HttpStatus.FORBIDDEN, "WORKING_CAPITAL_CYCLE_ACCESS_DENIED");
    }

    private ResponseEntity<?> result(HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("code", code));
    }
}
