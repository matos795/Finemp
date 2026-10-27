package com.finemp.connector.transport;

import com.finemp.connector.ConnectorIdentity;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix = "finemp.connector.protocol", name = "enabled", havingValue = "true")
@RequestMapping("/api/connector/v1/session")
public class ConnectorSessionController {
    @GetMapping
    public ResponseEntity<ConnectorSession> session(@AuthenticationPrincipal ConnectorIdentity identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(new ConnectorSession(identity.credentialId(), identity.accountingFirmId(), identity.accountingSourceId(), identity.version()));
    }
    public record ConnectorSession(UUID credentialId, UUID accountingFirmId, UUID accountingSourceId, long credentialVersion) { }
}
