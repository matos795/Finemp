package com.finemp.accounting.classification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DreClassificationCatalogService {

    private final DreClassificationRepository repository;

    public DreClassificationCatalogService(
            DreClassificationRepository repository) {

        this.repository = repository;
    }

    @Transactional
    public int createMissing(
            Collection<DreClassificationKey> requestedKeys) {

        Set<DreClassificationKey> existingKeys = repository.findAll()
                .stream()
                .map(DreClassificationKey::from)
                .collect(Collectors.toSet());

        var missing = requestedKeys.stream()
                .distinct()
                .filter(key -> !existingKeys.contains(key))
                .map(key -> new DreClassification(
                        key.dreClassification(),
                        key.dreOpenClassification(),
                        key.ebitdaClassification()))
                .toList();

        repository.saveAll(missing);

        return missing.size();
    }
}