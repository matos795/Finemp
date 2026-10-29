package com.finemp.finance.reporting;

import com.finemp.administration.OrganizationAccessDeniedException;
import org.springframework.http.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=OpenTitlesReadController.class)
public class OpenTitlesReadExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(){return result(HttpStatus.BAD_REQUEST,"INVALID_OPEN_TITLES");}
    @ExceptionHandler(OrganizationAccessDeniedException.class) ResponseEntity<?> denied(){return result(HttpStatus.FORBIDDEN,"OPEN_TITLES_ACCESS_DENIED");}
    private ResponseEntity<?> result(HttpStatus status,String code){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(java.util.Map.of("code",code));}
}
