package com.finemp.dre;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/dre")
public class DreController {

    private final DreCalculationService dreCalculationService;

    public DreController(
            DreCalculationService dreCalculationService) {
        this.dreCalculationService = dreCalculationService;
    }

    @GetMapping
    public DreCalculationResult calculate(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return dreCalculationService.calculate(
                companyId,
                startDate,
                endDate);
    }
}