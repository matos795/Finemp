package com.finemp.finance.bank;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount,UUID>{
    Optional<BankAccount> findByIdAndCompanyId(UUID id,UUID companyId);
    long countByCompanyId(UUID companyId);
    @Query("""
            select bank from BankAccount bank where bank.company.id=:companyId
              and (:search='' or locate(lower(:search),lower(bank.name))>0)
              and (:type is null or bank.type=:type) and (:active is null or bank.active=:active)
            """)
    Page<BankAccount> search(@Param("companyId") UUID companyId,@Param("search") String search,@Param("type") BankAccountType type,
            @Param("active") Boolean active,Pageable pageable);
}
