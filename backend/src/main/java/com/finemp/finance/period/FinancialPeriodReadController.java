package com.finemp.finance.period;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-periods")
@ConditionalOnProperty(prefix="finemp.finance.periods",name="enabled",havingValue="true")
public class FinancialPeriodReadController {
    private final FinancialPeriodReadService service;
    public FinancialPeriodReadController(FinancialPeriodReadService service) {this.service=service;}
    @GetMapping
    public PagedResponse<FinancialPeriodReadService.State> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) Boolean closed,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(actor.getUserAccountId(),companyId,from,to,closed,page,size);
    }
    @GetMapping("/{period}")
    public FinancialPeriodReadService.State find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable String period) {return service.find(actor.getUserAccountId(),companyId,period);}
    @GetMapping("/{period}/history")
    public PagedResponse<FinancialPeriodReadService.History> history(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable String period,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return service.history(actor.getUserAccountId(),companyId,period,page,size);}
}
