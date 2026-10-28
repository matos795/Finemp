package com.finemp.finance.transfer;

import com.finemp.security.UserAccountPrincipal;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.transfers",name={"enabled","commands.enabled"},havingValue="true")
@RequestMapping("/api/companies/{companyId}/bank-transfers")
public class BankTransferCommandController {
    private final BankTransferCommandService service;
    private final BankTransferCommandJsonDecoder decoder;
    public BankTransferCommandController(BankTransferCommandService service,BankTransferCommandJsonDecoder decoder) {this.service=service;this.decoder=decoder;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BankTransferCommandReceipt> create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var receipt=service.create(actor.getUserAccountId(),companyId,command,decoder.decode(body));
        return ResponseEntity.created(URI.create("/api/companies/"+companyId+"/bank-transfers/"+receipt.transferId())).body(receipt);
    }
    @DeleteMapping("/{transferId}")
    public BankTransferCommandReceipt delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID transferId,
            @RequestHeader("Idempotency-Key") UUID command,@RequestParam Long expectedVersion) {
        return service.delete(actor.getUserAccountId(),companyId,command,transferId,expectedVersion);
    }
}
