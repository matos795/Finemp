package com.finemp.connector.work;

import com.finemp.administration.SaasResourceNotFoundException;
import com.finemp.connector.completion.*;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = {"com.finemp.connector.work", "com.finemp.connector.completion"})
public class ConnectorWorkExceptionHandler {
    @ExceptionHandler(ConnectorCredentialUnavailableException.class)
    public ResponseEntity<Map<String, String>> unauthorized() { return response(401, "CONNECTOR_UNAUTHORIZED"); }
    @ExceptionHandler(SaasResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> missing() { return response(404, "CONNECTOR_WORK_NOT_FOUND"); }
    @ExceptionHandler(ConnectorWorkNotCurrentException.class)
    public ResponseEntity<Map<String, String>> conflict() { return response(409, "CONNECTOR_WORK_NOT_CURRENT"); }
    @ExceptionHandler(InvalidConnectorBatchException.class)
    public ResponseEntity<Map<String, String>> invalidBatch() { return response(400, "CONNECTOR_INVALID_REQUEST"); }
    @ExceptionHandler(ConnectorBatchResourceLimitException.class)
    public ResponseEntity<Map<String, String>> oversizedBatch() { return response(413, "CONNECTOR_PAYLOAD_TOO_LARGE"); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalid() { return response(400, "CONNECTOR_INVALID_REQUEST"); }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> unavailable() { return response(503, "CONNECTOR_UNAVAILABLE"); }
    private ResponseEntity<Map<String, String>> response(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("code", code));
    }
}
