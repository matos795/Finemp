package com.finemp.finance.reporting;

import com.finemp.administration.PagedResponse;
import com.finemp.finance.category.FinancialDirection;
import com.finemp.security.UserAccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/open-titles")
@ConditionalOnProperty(prefix="finemp.finance.open-titles",name="enabled",havingValue="true")
public class OpenTitlesReadController {
    private final OpenTitlesReadService reads;
    public OpenTitlesReadController(OpenTitlesReadService reads){this.reads=reads;}
    @GetMapping("/by-partner") public ResponseEntity<PagedResponse<OpenTitlesReadService.PartnerTotal>> byPartner(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam FinancialDirection direction,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,@RequestParam(defaultValue="ALL") OpenTitlesAgingService.Scope scope,
            @RequestParam(required=false) OpenTitlesAgingService.Band band,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.byPartner(actor.getUserAccountId(),companyId,direction,asOf,scope,band,page,size));}
    @GetMapping public ResponseEntity<PagedResponse<OpenTitlesReadService.View>> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam FinancialDirection direction,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf,@RequestParam(defaultValue="ALL") OpenTitlesAgingService.Scope scope,
            @RequestParam(required=false) OpenTitlesAgingService.Band band,@RequestParam(required=false) UUID partnerId,@RequestParam(defaultValue="false") boolean unassignedPartner,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(actor.getUserAccountId(),companyId,direction,asOf,scope,band,partnerId,unassignedPartner,page,size));}
}
