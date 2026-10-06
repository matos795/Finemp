package com.finemp.accounting.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountingSourceRepository
        extends JpaRepository<AccountingSource, UUID> {
}