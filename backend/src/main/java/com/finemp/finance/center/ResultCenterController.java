package com.finemp.finance.center;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="finemp.finance.catalog",name="enabled",havingValue="true")
@RequestMapping("/api/companies/{companyId}/result-centers")
public class ResultCenterController {
    private final ResultCenterService service;
    public ResultCenterController(ResultCenterService service) {this.service=service;}
    @GetMapping
    public PagedResponse<ResultCenterResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(defaultValue="") String search,@RequestParam(required=false) Boolean active,@RequestParam(required=false) UUID rootId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return service.list(actor.getUserAccountId(),companyId,search,active,rootId,page,size);
    }
    @GetMapping("/{centerId}")
    public ResultCenterResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID centerId) {
        return service.find(actor.getUserAccountId(),companyId,centerId);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ResultCenterResponse create(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@Valid @RequestBody ResultCenterCreateRequest request) {
        return service.create(actor.getUserAccountId(),companyId,request);
    }
    @PutMapping("/{centerId}")
    public ResultCenterResponse update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID centerId,@Valid @RequestBody ResultCenterUpdateRequest request) {
        return service.update(actor.getUserAccountId(),companyId,centerId,request);
    }
    @DeleteMapping("/{centerId}")
    public ResultCenterService.DeleteResult delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID centerId,@RequestParam long version) {
        return service.delete(actor.getUserAccountId(),companyId,centerId,version);
    }
    @PostMapping("/suggested-plan") @ResponseStatus(HttpStatus.CREATED)
    public List<ResultCenterResponse> suggested(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId) {
        return service.createSuggestedPlan(actor.getUserAccountId(),companyId);
    }
    @GetMapping("/settings")
    public ResultCenterService.SettingsResponse settings(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId) {
        return service.settings(actor.getUserAccountId(),companyId);
    }
    @PutMapping("/settings")
    public ResultCenterService.SettingsResponse settings(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@Valid @RequestBody ResultCenterSettingsRequest request) {
        return service.updateSettings(actor.getUserAccountId(),companyId,request);
    }
}
