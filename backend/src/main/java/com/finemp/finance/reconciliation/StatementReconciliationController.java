package com.finemp.finance.reconciliation;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/statement-items/{statementId}")
@ConditionalOnExpression("${finemp.finance.statement-actions.enabled:false} && ${finemp.finance.entries.enabled:false}")
public class StatementReconciliationController {
    private final StatementReconciliationCommandService commands;private final StatementReconciliationJsonDecoder decoder;
    public StatementReconciliationController(StatementReconciliationCommandService commands,StatementReconciliationJsonDecoder decoder){this.commands=commands;this.decoder=decoder;}

    @PostMapping(path="/reconcile",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatementReconciliationCommandService.Receipt> reconcile(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @PathVariable UUID statementId,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var input=decoder.reconcile(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(commands.reconcile(actor.getUserAccountId(),companyId,command,statementId,
                input.expectedStatementVersion(),input.entryId(),input.expectedEntryVersion()));
    }

    @PostMapping(path="/create-entry",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatementReconciliationCommandService.Receipt> createEntry(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @PathVariable UUID statementId,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var input=decoder.create(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(commands.createEntry(actor.getUserAccountId(),companyId,command,statementId,input.expectedStatementVersion(),input.values()));
    }
}
