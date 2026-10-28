package com.finemp.finance.transfer;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BankTransferRepository extends JpaRepository<BankTransfer,UUID> {
    Optional<BankTransfer> findByIdAndCompanyId(UUID id,UUID companyId);
    @Query(value="""
            SELECT EXISTS(SELECT 1 FROM financial_bank_transfers WHERE company_id=:company
                AND (origin_bank_account_id=:bank OR destination_bank_account_id=:bank))
            """,nativeQuery=true)
    @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.query.timeout",value="5000"))
    boolean hasBankReferences(@Param("company") UUID company,@Param("bank") UUID bank);
}
