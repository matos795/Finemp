package com.finemp.finance.reconciliation;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/statement-items/{statementId}/candidates")
@ConditionalOnProperty(prefix="finemp.finance.statement-candidates",name="enabled",havingValue="true")
public class StatementCandidateController {
    private final StatementCandidateReadService reads;
    public StatementCandidateController(StatementCandidateReadService reads){this.reads=reads;}
    @GetMapping public ResponseEntity<PagedResponse<StatementCandidateReadService.View>> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID statementId,
            @RequestParam(required=false) StatementCandidateReadService.Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reads.list(actor.getUserAccountId(),companyId,statementId,status,page,size));
    }
}
