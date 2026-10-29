package com.finemp.finance.reconciliation;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankStatementItemRepository extends JpaRepository<BankStatementItem,UUID> {
    Optional<BankStatementItem> findByIdAndCompanyId(UUID id,UUID companyId);
    boolean existsByCompanyIdAndBankAccountIdAndIdentityKey(UUID companyId,UUID bankAccountId,String identityKey);
    Page<BankStatementItem> findByCompanyIdAndBankAccountId(UUID companyId,UUID bankAccountId,Pageable pageable);
}
