package com.finemp.finance.entry;

import com.finemp.security.UserAccountPrincipal;
import com.finemp.finance.http.ExpectedVersionQuery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.entries",name={"enabled","commands.enabled"},havingValue="true")
@RequestMapping("/api/companies/{companyId}/financial-entries")
public class EntryDeletionController {
    private final EntryDeletionCommandService service;
    public EntryDeletionController(EntryDeletionCommandService service) {this.service=service;}
    @DeleteMapping("/{entryId}")
    public FinancialEntryCommandReceipt delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") UUID command,HttpServletRequest request) {
        return service.delete(actor.getUserAccountId(),companyId,command,entryId,ExpectedVersionQuery.required(request.getQueryString()));
    }
}
