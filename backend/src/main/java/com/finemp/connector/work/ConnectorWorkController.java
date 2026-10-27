package com.finemp.connector.work;

import com.finemp.connector.ConnectorIdentity;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
@RequestMapping("/api/connector/v1/work")
public class ConnectorWorkController {
    private final ConnectorWorkService service;
    public ConnectorWorkController(ConnectorWorkService service) { this.service = service; }
    @PostMapping("/claim")
    public ResponseEntity<ConnectorWorkResponse> claim(@AuthenticationPrincipal ConnectorIdentity identity) {
        var work = service.claim(identity);
        return work.map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
    @PostMapping("/{attemptId}/heartbeat")
    public ResponseEntity<ConnectorLeaseResponse> renew(@AuthenticationPrincipal ConnectorIdentity identity, @PathVariable UUID attemptId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.renew(identity, attemptId));
    }
}
