package com.finemp.finance.period;

import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-periods/{period}")
@ConditionalOnProperty(prefix="finemp.finance.periods",name={"enabled","commands.enabled"},havingValue="true")
public class FinancialPeriodController {
    private final FinancialPeriodCommandService service;private final PeriodCommandJsonDecoder decoder;
    public FinancialPeriodController(FinancialPeriodCommandService service,PeriodCommandJsonDecoder decoder) {this.service=service;this.decoder=decoder;}
    @PostMapping(value="/closures",consumes=MediaType.APPLICATION_JSON_VALUE)
    public FinancialPeriodReceipt close(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable String period,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decode(body,false);return service.close(actor.getUserAccountId(),companyId,command,PeriodCommandJsonDecoder.period(period),request.expectedVersion());
    }
    @PostMapping(value="/reopenings",consumes=MediaType.APPLICATION_JSON_VALUE)
    public FinancialPeriodReceipt reopen(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable String period,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decode(body,true);return service.reopen(actor.getUserAccountId(),companyId,command,PeriodCommandJsonDecoder.period(period),request.expectedVersion(),request.reason());
    }
}
