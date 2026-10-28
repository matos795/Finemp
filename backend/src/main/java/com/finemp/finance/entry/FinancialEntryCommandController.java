package com.finemp.finance.entry;

import com.finemp.security.UserAccountPrincipal;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.entries",name={"enabled","commands.enabled"},havingValue="true")
@RequestMapping("/api/companies/{companyId}/financial-entries")
public class FinancialEntryCommandController {
    private final FinancialEntryCommandService service;
    private final EntryCommandJsonDecoder decoder;
    public FinancialEntryCommandController(FinancialEntryCommandService service,EntryCommandJsonDecoder decoder) {this.service=service;this.decoder=decoder;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FinancialEntryCommandReceipt> create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decode(body);if(request.expectedVersion()!=null)throw new IllegalArgumentException("Versão não se aplica à criação.");
        var receipt=service.create(actor.getUserAccountId(),companyId,command,request.values());
        return ResponseEntity.created(URI.create("/api/companies/"+companyId+"/financial-entries/"+receipt.entryId())).body(receipt);
    }
    @PutMapping(path="/{entryId}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public FinancialEntryCommandReceipt update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decode(body);
        return service.update(actor.getUserAccountId(),companyId,command,entryId,request.expectedVersion(),request.values());
    }
}
