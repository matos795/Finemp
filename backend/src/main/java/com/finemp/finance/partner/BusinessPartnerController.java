package com.finemp.finance.partner;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@RequestMapping("/api/companies/{companyId}/business-partners")
public class BusinessPartnerController {
    private final BusinessPartnerService service;
    public BusinessPartnerController(BusinessPartnerService service) {this.service=service;}
    @GetMapping
    public PagedResponse<BusinessPartnerResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(defaultValue="") String search,@RequestParam(required=false) PartnerRole role,@RequestParam(required=false) Boolean active,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return service.list(actor.getUserAccountId(),companyId,search,role,active,page,size);
    }
    @GetMapping("/{partnerId}")
    public BusinessPartnerResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID partnerId) {
        return service.find(actor.getUserAccountId(),companyId,partnerId);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BusinessPartnerResponse create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@Valid @RequestBody BusinessPartnerRequest request) {
        return service.create(actor.getUserAccountId(),companyId,request);
    }
    @PutMapping("/{partnerId}")
    public BusinessPartnerResponse update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID partnerId,@Valid @RequestBody BusinessPartnerRequest request) {
        return service.update(actor.getUserAccountId(),companyId,partnerId,request);
    }
    @DeleteMapping("/{partnerId}")
    public BusinessPartnerService.DeleteResult delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID partnerId,@RequestParam long version) {
        return service.delete(actor.getUserAccountId(),companyId,partnerId,version);
    }
}
