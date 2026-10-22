package com.finemp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUserQueryService currentUserQueryService;

    public AuthenticationController(
            AuthenticationService authenticationService,
            SecurityContextRepository securityContextRepository,
            CurrentUserQueryService currentUserQueryService) {

        this.authenticationService = authenticationService;
        this.securityContextRepository = securityContextRepository;
        this.currentUserQueryService = currentUserQueryService;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        Authentication authentication = authenticationService.authenticate(
                loginRequest.email(),
                loginRequest.password());

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);

        SecurityContextHolder.setContext(securityContext);

        securityContextRepository.saveContext(
                securityContext,
                request,
                response);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal UserAccountPrincipal principal) {

        return currentUserQueryService.findCurrentUser(
                principal.getUserAccountId());
    }
}