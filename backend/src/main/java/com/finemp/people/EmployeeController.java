package com.finemp.people;

import com.finemp.administration.PagedResponse;
import com.finemp.security.UserAccountPrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies/{companyId}/employees")
public class EmployeeController {

    private final PeopleService service;

    public EmployeeController(PeopleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<EmployeeResponse>> list(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) UUID jobPositionId,
            @RequestParam(required = false) UUID resultCenterId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listEmployees(actor.getUserAccountId(), companyId, search, employmentType,
                        jobPositionId, resultCenterId, status, page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @RequestBody EmployeeRequest request) {
        return service.createEmployee(actor.getUserAccountId(), companyId, request);
    }

    @PutMapping("/{employeeId}")
    public EmployeeResponse update(
            @AuthenticationPrincipal UserAccountPrincipal actor,
            @PathVariable UUID companyId,
            @PathVariable UUID employeeId,
            @RequestBody EmployeeRequest request) {
        return service.updateEmployee(actor.getUserAccountId(), companyId, employeeId, request);
    }
}
