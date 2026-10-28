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
@RequestMapping("/api/companies/{companyId}/financial-entry-series/monthly")
public class MonthlyEntryController {
    private final MonthlyEntryCommandService service;
    private final EntryCommandJsonDecoder decoder;
    public MonthlyEntryController(MonthlyEntryCommandService service,EntryCommandJsonDecoder decoder) {this.service=service;this.decoder=decoder;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntrySeriesCommandReceipt> create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decodeMonthly(body);var receipt=service.create(actor.getUserAccountId(),companyId,command,request.quantity(),request.values());
        return ResponseEntity.created(URI.create("/api/companies/"+companyId+"/financial-entries/"+receipt.items().getFirst().entryId())).body(receipt);
    }
}
