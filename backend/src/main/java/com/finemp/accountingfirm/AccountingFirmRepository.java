package com.finemp.accountingfirm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AccountingFirmRepository extends JpaRepository<AccountingFirm, UUID> {

    Page<AccountingFirm> findAllByNameContainingIgnoreCase(
            String search,
            Pageable pageable);
}
