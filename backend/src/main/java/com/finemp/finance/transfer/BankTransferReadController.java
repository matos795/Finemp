package com.finemp.finance.transfer;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.transfers",name="enabled",havingValue="true")
@RequestMapping("/api/companies/{companyId}/bank-transfers")
public class BankTransferReadController {
    private final BankTransferReadService service;
    public BankTransferReadController(BankTransferReadService service) {this.service=service;}
    @GetMapping
    public PagedResponse<BankTransferResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return service.list(actor.getUserAccountId(),companyId,page,size);}
    @GetMapping("/{transferId}")
    public BankTransferResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID transferId) {
        return service.find(actor.getUserAccountId(),companyId,transferId);
    }
}
