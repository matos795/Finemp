package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ReceivedPaymentsByMethodController.class)
public class ReceivedPaymentsByMethodExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> invalid() {
        return result(HttpStatus.BAD_REQUEST, "INVALID_RECEIVED_PAYMENTS_PERIOD");
    }

    @ExceptionHandler(OrganizationAccessDeniedException.class)
    ResponseEntity<?> denied() {
        return result(HttpStatus.FORBIDDEN, "RECEIVED_PAYMENTS_ACCESS_DENIED");
    }

    private ResponseEntity<?> result(HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("code", code));
    }
}
