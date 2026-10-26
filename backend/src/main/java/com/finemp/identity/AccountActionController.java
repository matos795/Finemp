package com.finemp.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "finemp.identity.mail", name = "enabled", havingValue = "true")
public class AccountActionController {

    private final AccountActionService service;
    private final IdentityAttemptGuard guard;

    public AccountActionController(AccountActionService service, IdentityAttemptGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        if (guard.allowPasswordReset(request.getRemoteAddr(), body.email())) {
            service.requestPasswordReset(body.email());
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<Void> reset(@Valid @RequestBody CompleteAccountActionRequest body, HttpServletRequest request) {
        return complete(body, AccountActionPurpose.PASSWORD_RESET, request);
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody CompleteAccountActionRequest body, HttpServletRequest request) {
        return complete(body, AccountActionPurpose.INVITATION, request);
    }

    private ResponseEntity<Void> complete(CompleteAccountActionRequest body, AccountActionPurpose purpose,
                                        HttpServletRequest request) {
        guard.requireCompletion(request.getRemoteAddr());
        service.complete(body.token(), purpose, body.newPassword());
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
