package com.finemp.accounting.movement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountingMovementRepository
        extends JpaRepository<AccountingMovement, UUID> {
}