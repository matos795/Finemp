package com.finemp.connector;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@ConditionalOnProperty(prefix = "finemp.connector", name = "enabled", havingValue = "true")
@RequestMapping("/api/admin/accounting-firms/{firmId}/connectors")
public class ConnectorCredentialController {
    private final ConnectorCredentialService service;
    public ConnectorCredentialController(ConnectorCredentialService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ConnectorSecretResponse> issue(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID firmId,
            @RequestBody @Valid CreateConnectorCredentialRequest request) {
        return secret(HttpStatus.CREATED, service.issue(user.getUserAccountId(), firmId, request));
    }
    @GetMapping
    public ResponseEntity<PagedResponse<ConnectorCredentialResponse>> list(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID firmId,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user.getUserAccountId(), firmId, search, page, size));
    }
    @PostMapping("/{credentialId}/rotate")
    public ResponseEntity<ConnectorSecretResponse> rotate(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID firmId,
            @PathVariable UUID credentialId) {
        return secret(HttpStatus.OK, service.rotate(user.getUserAccountId(), firmId, credentialId));
    }
    @DeleteMapping("/{credentialId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserAccountPrincipal user, @PathVariable UUID firmId, @PathVariable UUID credentialId) {
        service.revoke(user.getUserAccountId(), firmId, credentialId);
    }
    private ResponseEntity<ConnectorSecretResponse> secret(HttpStatus status, ConnectorSecretResponse value) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(value);
    }
    @ExceptionHandler(ConnectorCredentialConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public java.util.Map<String, String> conflict() { return java.util.Map.of("code", "CONNECTOR_STATE_CONFLICT"); }
}
