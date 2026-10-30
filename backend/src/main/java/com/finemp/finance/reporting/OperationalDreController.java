package com.finemp.finance.reporting;

import com.finemp.security.UserAccountPrincipal;
import com.finemp.finance.category.OperationalDreLine;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-reports/operational-dre")
@ConditionalOnProperty(prefix="finemp.finance.operational-dre",name="enabled",havingValue="true")
public class OperationalDreController {
    private final OperationalDreMonthlyReadService reports;
    private final OperationalDreEffectReadService effects;
    public OperationalDreController(OperationalDreMonthlyReadService reports,OperationalDreEffectReadService effects){this.reports=reports;this.effects=effects;}
    @GetMapping("/effects") public ResponseEntity<OperationalDreEffectReadService.Result> effects(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam String month,@RequestParam(required=false) OperationalDreLine line,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(effects.list(actor.getUserAccountId(),companyId,month,line,page,size));
    }
    @GetMapping public ResponseEntity<OperationalDreMonthlyReadService.Report> report(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@RequestParam String month){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(actor.getUserAccountId(),companyId,month));
    }
}
