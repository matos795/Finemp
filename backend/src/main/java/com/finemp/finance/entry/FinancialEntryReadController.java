package com.finemp.finance.entry;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.entries",name="enabled",havingValue="true")
@RequestMapping("/api/companies/{companyId}/financial-entries")
public class FinancialEntryReadController {
    private final FinancialEntryReadService service;
    public FinancialEntryReadController(FinancialEntryReadService service) {this.service=service;}
    @GetMapping
    public PagedResponse<FinancialEntryResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @ModelAttribute FinancialEntryFilter filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return service.list(actor.getUserAccountId(),companyId,filter,page,size);
    }
    @GetMapping("/{entryId}")
    public FinancialEntryResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return service.find(actor.getUserAccountId(),companyId,entryId,asOfDate);
    }
}
