package com.finemp.finance.bank;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@RequestMapping("/api/companies/{companyId}/bank-accounts")
public class BankAccountController{
    private final BankAccountService service;
    public BankAccountController(BankAccountService service){this.service=service;}
    @GetMapping
    public PagedResponse<BankAccountResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(defaultValue="") String search,@RequestParam(required=false) BankAccountType type,@RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        return service.list(actor.getUserAccountId(),companyId,search,type,active,page,size);
    }
    @GetMapping("/{bankId}")
    public BankAccountResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId){return service.find(actor.getUserAccountId(),companyId,bankId);}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@Valid @RequestBody BankAccountRequest request){return service.create(actor.getUserAccountId(),companyId,request);}
    @PutMapping("/{bankId}")
    public BankAccountResponse update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId,@Valid @RequestBody BankAccountRequest request){return service.update(actor.getUserAccountId(),companyId,bankId,request);}
    @DeleteMapping("/{bankId}")
    public BankAccountService.DeleteResult delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId,@RequestParam long version){return service.delete(actor.getUserAccountId(),companyId,bankId,version);}
}
