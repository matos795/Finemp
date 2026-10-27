package com.finemp.identity;

import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounting-firms/{firmId}/invitations")
@ConditionalOnProperty(prefix = "finemp.identity.mail", name = "enabled", havingValue = "true")
public class FirmInvitationController {

    private final FirmInvitationService service;
    private final IdentityAttemptGuard guard;

    public FirmInvitationController(FirmInvitationService service, IdentityAttemptGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping
    public ResponseEntity<Void> invite(@AuthenticationPrincipal UserAccountPrincipal principal,
                                     @PathVariable UUID firmId, @Valid @RequestBody InviteFirmUserRequest body) {
        guard.requireInvitation(principal.getUserAccountId());
        service.invite(principal.getUserAccountId(), firmId, body);
        return ResponseEntity.noContent().build();
    }
}
