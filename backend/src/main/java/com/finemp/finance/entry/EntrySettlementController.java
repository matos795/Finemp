package com.finemp.finance.entry;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.entries",name={"enabled","commands.enabled"},havingValue="true")
@RequestMapping("/api/companies/{companyId}/financial-entries/{entryId}")
public class EntrySettlementController {
    private final EntrySettlementCommandService service;
    private final EntryCommandJsonDecoder decoder;
    public EntrySettlementController(EntrySettlementCommandService service,EntryCommandJsonDecoder decoder) {this.service=service;this.decoder=decoder;}
    @PostMapping(path="/settlements",consumes=MediaType.APPLICATION_JSON_VALUE)
    public FinancialEntryCommandReceipt settle(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decodeSettlement(body);
        return service.settle(actor.getUserAccountId(),companyId,command,entryId,request.expectedVersion(),request.values());
    }
    @PostMapping(path="/settlement-reversals",consumes=MediaType.APPLICATION_JSON_VALUE)
    public FinancialEntryCommandReceipt reverse(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        return service.reverse(actor.getUserAccountId(),companyId,command,entryId,decoder.decodeReversal(body));
    }
}
