package com.finemp.finance.reconciliation;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/bank-accounts/{bankId}/statement-items")
@ConditionalOnProperty(prefix="finemp.finance.statement-read",name="enabled",havingValue="true")
public class StatementItemReadController {
    private final StatementItemReadService reads;
    public StatementItemReadController(StatementItemReadService reads){this.reads=reads;}
    @GetMapping public ResponseEntity<PagedResponse<StatementItemReadService.View>> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId,
            @RequestParam(required=false) StatementStatus status,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(actor.getUserAccountId(),companyId,bankId,status,from,to,page,size));
    }
}
