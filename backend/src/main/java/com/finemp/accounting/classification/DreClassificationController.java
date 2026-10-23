package com.finemp.accounting.classification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies/{companyId}/dre-classifications")
public class DreClassificationController {

    private final DreClassificationRepository dreClassificationRepository;

    public DreClassificationController(
            DreClassificationRepository dreClassificationRepository) {
        this.dreClassificationRepository = dreClassificationRepository;
    }

    @GetMapping
    public List<DreClassificationResponse> findAll() {

        return dreClassificationRepository
                .findAll()
                .stream()
                .map(DreClassificationResponse::from)
                .toList();
    }
}