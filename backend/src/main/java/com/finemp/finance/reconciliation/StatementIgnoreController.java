package com.finemp.finance.reconciliation;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/statement-items/{statementId}/ignore")
@ConditionalOnProperty(prefix="finemp.finance.statement-actions",name="enabled",havingValue="true")
public class StatementIgnoreController {
    private final StatementIgnoreCommandService commands;private final StatementIgnoreJsonDecoder decoder;
    public StatementIgnoreController(StatementIgnoreCommandService commands,StatementIgnoreJsonDecoder decoder){this.commands=commands;this.decoder=decoder;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StatementIgnoreCommandService.Receipt> ignore(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID statementId,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(commands.ignore(actor.getUserAccountId(),companyId,command,statementId,decoder.decode(body)));
    }
}
