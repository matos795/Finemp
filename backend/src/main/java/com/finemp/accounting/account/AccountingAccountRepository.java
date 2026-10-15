package com.finemp.accounting.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountRepository
                extends JpaRepository<AccountingAccount, UUID> {

        Optional<AccountingAccount> findByAccountingSourceIdAndExternalAccountId(
                        UUID accountingSourceId,
                        String externalAccountId);

        @Query("""
                        select account
                        from AccountingAccount account
                        where account.accountingSource.id = :accountingSourceId
                          and not exists (
                              select classification.id
                              from AccountingAccountClassification classification
                              where classification.accountingAccount = account
                          )
                        order by account.accountKey
                        """)
        List<AccountingAccount> findAllWithoutClassificationByAccountingSourceId(
                        @Param("accountingSourceId") UUID accountingSourceId);
}