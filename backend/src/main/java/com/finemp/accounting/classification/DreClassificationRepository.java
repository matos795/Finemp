package com.finemp.accounting.classification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DreClassificationRepository
        extends JpaRepository<DreClassification, UUID> {
}