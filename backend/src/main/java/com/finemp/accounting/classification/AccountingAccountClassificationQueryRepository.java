package com.finemp.accounting.classification;

import com.finemp.accounting.account.AccountingAccount;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AccountingAccountClassificationQueryRepository
        extends Repository<AccountingAccount, UUID> {

    @Query("""
            select new com.finemp.accounting.classification.AccountingAccountClassificationRow(
                account.id,
                source.id,
                account.accountKey,
                account.description,
                dreClassification.id,
                dreClassification.dreClassification,
                dreClassification.dreOpenClassification,
                dreClassification.ebitdaClassification
            )
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
            order by account.accountKey
            """)
    List<AccountingAccountClassificationRow> findAllByCompanyId(
            @Param("companyId") UUID companyId);

    @Query(value = """
            select new com.finemp.accounting.classification.AccountingAccountClassificationRow(
                account.id,
                source.id,
                account.accountKey,
                account.description,
                dreClassification.id,
                dreClassification.dreClassification,
                dreClassification.dreOpenClassification,
                dreClassification.ebitdaClassification
            )
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
              and (
                  :search is null
                  or lower(account.accountKey) like lower(concat('%', :search, '%'))
                  or lower(account.description) like lower(concat('%', :search, '%'))
              )
              and (
                  :status is null
                  or (:status = 'PENDING' and dreClassification.id is null)
                  or (:status = 'CLASSIFIED' and dreClassification.id is not null)
              )
            order by account.accountKey
            """, countQuery = """
            select count(account.id)
            from AccountingAccount account
            join account.accountingSource source
            left join AccountingAccountClassification accountClassification
                on accountClassification.accountingAccount = account
            left join accountClassification.classification dreClassification
            where source.company.id = :companyId
              and (
                  :search is null
                  or lower(account.accountKey) like lower(concat('%', :search, '%'))
                  or lower(account.description) like lower(concat('%', :search, '%'))
              )
              and (
                  :status is null
                  or (:status = 'PENDING' and dreClassification.id is null)
                  or (:status = 'CLASSIFIED' and dreClassification.id is not null)
              )
            """)
    Page<AccountingAccountClassificationRow> findPageByCompanyId(
            @Param("companyId") UUID companyId,
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    @Query("""
            select count(account.id)
            from AccountingAccount account
            join account.accountingSource source
            where source.company.id = :companyId
            """)
    long countByCompanyId(
            @Param("companyId") UUID companyId);

    @Query("""
            select count(account.id)
            from AccountingAccount account
            join account.accountingSource source
            join AccountingAccountClassification classification
                on classification.accountingAccount = account
            where source.company.id = :companyId
            """)
    long countClassifiedByCompanyId(
            @Param("companyId") UUID companyId);
}