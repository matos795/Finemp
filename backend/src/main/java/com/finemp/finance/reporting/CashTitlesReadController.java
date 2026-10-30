package com.finemp.finance.reporting;

import com.finemp.administration.PagedResponse;
import com.finemp.finance.category.*;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/monthly-cash-totals/titles")
@ConditionalOnProperty(prefix="finemp.finance.monthly-cash",name="enabled",havingValue="true")
public class CashTitlesReadController {
    private final CashTitlesReadService reads;
    public CashTitlesReadController(CashTitlesReadService reads){this.reads=reads;}
    @GetMapping public ResponseEntity<PagedResponse<CashTitlesReadService.View>> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam String month,@RequestParam FinancialDirection direction,@RequestParam CashTitlesReadService.State state,
            @RequestParam(required=false) CashActivity activity,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(actor.getUserAccountId(),companyId,month,direction,state,activity,page,size));
    }
}
