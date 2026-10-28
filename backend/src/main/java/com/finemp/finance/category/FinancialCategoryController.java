package com.finemp.finance.category;

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
@RequestMapping("/api/companies/{companyId}/financial-categories")
public class FinancialCategoryController {
    private final FinancialCategoryService service;
    public FinancialCategoryController(FinancialCategoryService service) {this.service=service;}
    @GetMapping
    public PagedResponse<FinancialCategoryResponse> list(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,
            @RequestParam(defaultValue="") String search,@RequestParam(required=false) FinancialDirection direction,
            @RequestParam(required=false) FinancialCategory.Kind kind,@RequestParam(required=false) UUID parentId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return service.list(actor.getUserAccountId(),companyId,search,direction,kind,parentId,page,size);
    }
    @GetMapping("/{categoryId}")
    public FinancialCategoryResponse find(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID categoryId) {
        return service.find(actor.getUserAccountId(),companyId,categoryId);
    }
    @PostMapping("/groups") @ResponseStatus(HttpStatus.CREATED)
    public FinancialCategoryResponse createGroup(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@Valid @RequestBody FinancialCategoryGroupRequest request) {
        return service.createGroup(actor.getUserAccountId(),companyId,request);
    }
    @PostMapping("/{groupId}/subcategories") @ResponseStatus(HttpStatus.CREATED)
    public FinancialCategoryResponse createSubcategory(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID groupId,@Valid @RequestBody FinancialSubcategoryRequest request) {
        return service.createSubcategory(actor.getUserAccountId(),companyId,groupId,request);
    }
    @PutMapping("/{categoryId}")
    public FinancialCategoryResponse update(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID categoryId,@Valid @RequestBody FinancialCategoryUpdateRequest request) {
        return service.update(actor.getUserAccountId(),companyId,categoryId,request);
    }
    @DeleteMapping("/{categoryId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID categoryId,@RequestParam long version) {
        service.delete(actor.getUserAccountId(),companyId,categoryId,version);
    }
}
