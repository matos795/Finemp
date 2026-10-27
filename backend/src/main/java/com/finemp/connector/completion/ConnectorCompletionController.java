package com.finemp.connector.completion;

import com.finemp.connector.ConnectorIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
@RequestMapping("/api/connector/v1/work/{attemptId}")
public class ConnectorCompletionController {
    private final ConnectorCompletionService service;
    private final ConnectorCompletionContext context;
    public ConnectorCompletionController(ConnectorCompletionService service, ConnectorCompletionContext context) { this.service = service; this.context = context; }
    @PostMapping(value = "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConnectorWorkResult> complete(@AuthenticationPrincipal ConnectorIdentity identity, @PathVariable UUID attemptId,
            HttpServletRequest request) throws IOException {
        var outcome = service.complete(identity, attemptId, request.getInputStream());
        return ResponseEntity.status(outcome.httpStatus()).cacheControl(CacheControl.noStore()).body(outcome.result());
    }
    @PostMapping(value = "/fail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConnectorWorkResult> fail(@AuthenticationPrincipal ConnectorIdentity identity, @PathVariable UUID attemptId,
            HttpServletRequest request) throws IOException {
        var outcome = service.reportFailure(identity, attemptId, request.getInputStream());
        return ResponseEntity.status(outcome.httpStatus()).cacheControl(CacheControl.noStore()).body(outcome.result());
    }
    @GetMapping("/result")
    public ResponseEntity<ConnectorWorkResult> result(@AuthenticationPrincipal ConnectorIdentity identity, @PathVariable UUID attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(context.result(identity, attemptId));
    }
}
