package com.finemp.administration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyClientAssignmentEventRepository
        extends JpaRepository<CompanyClientAssignmentEvent, UUID> {
}
