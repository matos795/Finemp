package com.finemp.accounting.source;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/accounting-sources")
public class AccountingSourceReadController {
    private final AccountingSourceRepository sources;

    public AccountingSourceReadController(AccountingSourceRepository sources) {
        this.sources = sources;
    }

    @GetMapping("/readable")
    public ResponseEntity<List<ReadableAccountingSourceResponse>> listReadable(
            @PathVariable UUID companyId) {
        var result = sources.findAllByCompanyId(
                        companyId,
                        Sort.by(Sort.Order.asc("externalReference").nullsLast(), Sort.Order.asc("id")))
                .stream()
                .map(ReadableAccountingSourceResponse::from)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
