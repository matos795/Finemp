package com.finemp.accountingfirm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountingFirmRepository extends JpaRepository<AccountingFirm, UUID> {
}

