package com.finemp.finance.withholding;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-entries/{entryId}/withholdings")
@ConditionalOnProperty(prefix="finemp.finance.withholdings",name="enabled",havingValue="true")
public class WithholdingController {
    private final WithholdingCommandService commands;private final WithholdingReadService reads;private final WithholdingJsonDecoder decoder;
    public WithholdingController(WithholdingCommandService commands,WithholdingReadService reads,WithholdingJsonDecoder decoder) {this.commands=commands;this.reads=reads;this.decoder=decoder;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WithholdingReceipt> create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {
        var request=decoder.decode(body,WithholdingAction.CREATED);var receipt=commands.create(actor.getUserAccountId(),companyId,command,entryId,request.entryVersion(),request.values());return ResponseEntity.created(URI.create("/api/companies/"+companyId+"/financial-entries/"+entryId+"/withholdings/"+receipt.withholdingId())).body(receipt);
    }
    @PutMapping(value="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public WithholdingReceipt update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {var request=decoder.decode(body,WithholdingAction.UPDATED);return commands.update(actor.getUserAccountId(),companyId,command,entryId,request.entryVersion(),id,request.expectedVersion(),request.values());}
    @PostMapping(value="/{id}/deletions",consumes=MediaType.APPLICATION_JSON_VALUE)
    public WithholdingReceipt delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID command,@RequestBody byte[] body) {var request=decoder.decode(body,WithholdingAction.DELETED);return commands.delete(actor.getUserAccountId(),companyId,command,entryId,request.entryVersion(),id,request.expectedVersion());}
    @GetMapping
    public PagedResponse<WithholdingReadService.View> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@RequestParam(required=false) WithholdingTreatment treatment,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return reads.list(actor.getUserAccountId(),companyId,entryId,treatment,page,size);}
    @GetMapping("/{id}")
    public WithholdingReadService.View find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@PathVariable UUID id) {return reads.find(actor.getUserAccountId(),companyId,entryId,id);}
    @GetMapping("/{id}/history")
    public PagedResponse<WithholdingReadService.History> history(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID entryId,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return reads.history(actor.getUserAccountId(),companyId,entryId,id,page,size);}
}
